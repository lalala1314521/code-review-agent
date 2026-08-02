package io.github.lalala1314521.codereviewagent.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.model.Verdict;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewFindingEntity;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewRecordEntity;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewFindingMapper;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审查记录持久化服务：落库四个生命周期节点——PENDING（{@link #createPendingRecord}）、
 * REVIEWING（{@link #markReviewing}）、DONE + findings（{@link #completeRecord}）、FAILED（{@link #failRecord}）。
 *
 * <p>设计要点：<b>落库失败绝不影响审查主流程</b>——DB 抖动时评论回写仍要发出，
 * 调用方都应 try-catch 仅告警（DB 挂了最多丢统计，不丢功能）。
 * 幂等双保险：Redis SETNX 挡 99% 重复，uk_commit 唯一索引兜底（捕获 DuplicateKeyException 返回 null）。
 */
@Service
public class ReviewRecordService {

    private static final Logger log = LoggerFactory.getLogger(ReviewRecordService.class);

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REVIEWING = "REVIEWING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    private final ReviewRecordMapper recordMapper;
    private final ReviewFindingMapper findingMapper;

    public ReviewRecordService(ReviewRecordMapper recordMapper, ReviewFindingMapper findingMapper) {
        this.recordMapper = recordMapper;
        this.findingMapper = findingMapper;
    }

    /**
     * webhook 触发后插入 PENDING 记录。
     *
     * @return 记录 id；若撞 uk_commit 唯一索引（Redis 失效后的重复请求）返回 null，调用方应跳过后续审查
     */
    public Long createPendingRecord(UnifiedMergeRequest mr) {
        ReviewRecordEntity entity = new ReviewRecordEntity();
        entity.setTraceId(UUID.randomUUID().toString());
        entity.setPlatform(mr.platform());
        entity.setProjectId(mr.projectId());
        entity.setRepoPath(mr.repoPath());
        entity.setMrIid(mr.mrIid());
        entity.setCommitSha(mr.commitSha());
        entity.setSourceBranch(mr.sourceBranch());
        entity.setTargetBranch(mr.targetBranch());
        entity.setTitle(mr.title());
        entity.setAuthorUsername(mr.authorUsername());
        entity.setStatus(STATUS_PENDING);
        entity.setTriggeredAt(LocalDateTime.now());

        try {
            recordMapper.insert(entity);
            log.info("review record created id={} traceId={} project={} mr={}",
                    entity.getId(), entity.getTraceId(), mr.projectId(), mr.mrIid());
            return entity.getId();
        } catch (DuplicateKeyException e) {
            // DB 层幂等兜底：uk_commit 已存在 → 重复请求
            log.warn("duplicate review record (uk_commit hit), skip project={} mr={} commit={}",
                    mr.projectId(), mr.mrIid(), mr.commitSha());
            return null;
        }
    }

    /**
     * 异步任务开始执行 → 状态置 REVIEWING。
     */
    public void markReviewing(Long recordId) {
        ReviewRecordEntity update = new ReviewRecordEntity();
        update.setId(recordId);
        update.setStatus(STATUS_REVIEWING);
        update.setStartedAt(LocalDateTime.now());
        recordMapper.updateById(update);
    }

    /**
     * 审查完成 → 更新主记录 + 批量插入 findings。
     *
     * <p>事务边界：主记录与 findings 要么都成功要么都失败，
     * 避免出现"显示 DONE 但查不到发现"的中间态。
     *
     * <p>confidence 单位换算：内存模型 0~1 → DB 0~100（DECIMAL(5,2)）。
     */
    @Transactional
    public void completeRecord(Long recordId, Verdict verdict, long durationMs) {
        ReviewRecordEntity update = new ReviewRecordEntity();
        update.setId(recordId);
        update.setStatus(STATUS_DONE);
        update.setConclusion(verdict.conclusion().name());
        update.setConfidence(toPercent(verdict.confidence()));
        update.setErrorCount(verdict.errorCount());
        update.setWarningCount(verdict.warningCount());
        update.setInfoCount(verdict.infoCount());
        update.setDurationMs(durationMs);
        update.setFinishedAt(LocalDateTime.now());
        recordMapper.updateById(update);

        // 批量插入 findings（一次审查通常 <50 条，逐条 insert 可接受）
        List<ReviewFinding> findings = verdict.findings();
        if (findings != null && !findings.isEmpty()) {
            for (ReviewFinding f : findings) {
                findingMapper.insert(toFindingEntity(recordId, f));
            }
        }
        log.info("review record completed id={} conclusion={} findings={} durationMs={}",
                recordId, verdict.conclusion(), findings == null ? 0 : findings.size(), durationMs);
    }

    /**
     * 审查异常 → 状态置 FAILED。
     */
    public void failRecord(Long recordId, long durationMs) {
        ReviewRecordEntity update = new ReviewRecordEntity();
        update.setId(recordId);
        update.setStatus(STATUS_FAILED);
        update.setDurationMs(durationMs);
        update.setFinishedAt(LocalDateTime.now());
        recordMapper.updateById(update);
        log.warn("review record failed id={} durationMs={}", recordId, durationMs);
    }

    /**
     * 按幂等四元组查询已有记录（uk_commit 冲突时返回已有数据用）。
     */
    public ReviewRecordEntity findByCommitKey(String platform, Long projectId, Long mrIid, String commitSha) {
        return recordMapper.selectOne(new QueryWrapper<ReviewRecordEntity>()
                .eq("platform", platform)
                .eq("project_id", projectId)
                .eq("mr_iid", mrIid)
                .eq("commit_sha", commitSha));
    }

    /**
     * ReviewFinding（内存模型）→ ReviewFindingEntity（DB 模型）。
     */
    private ReviewFindingEntity toFindingEntity(Long recordId, ReviewFinding f) {
        ReviewFindingEntity entity = new ReviewFindingEntity();
        entity.setReviewRecordId(recordId);
        entity.setFilePath(f.filePath() != null ? f.filePath() : "(unknown)");
        entity.setLineNumber(f.lineNumber());
        entity.setSeverity(f.severity().name());
        entity.setRuleId(f.ruleId());
        entity.setMessage(f.message());
        entity.setSuggestion(f.suggestion());
        entity.setSource(f.source() != null ? f.source() : "LLM");
        entity.setConfidence(toPercent(f.confidence()));
        return entity;
    }

    /**
     * 0~1 → 0~100（两位小数）；null 容忍。
     */
    private BigDecimal toPercent(Double ratio) {
        if (ratio == null) {
            return null;
        }
        return BigDecimal.valueOf(ratio * 100).setScale(2, RoundingMode.HALF_UP);
    }
}
