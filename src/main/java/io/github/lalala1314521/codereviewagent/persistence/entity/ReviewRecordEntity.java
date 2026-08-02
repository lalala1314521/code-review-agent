package io.github.lalala1314521.codereviewagent.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审查记录主表实体（review_record）。
 *
 * <p>一次 webhook 触发对应一条记录，生命周期：
 * PENDING（已入库待审）→ REVIEWING（审查中）→ DONE / FAILED。
 *
 * <p>幂等双保险：Redis SETNX（快，挡 99% 重复）+ uk_commit 唯一索引（DB 兜底，
 * 防 Redis 失效/多实例竞争）。
 */
@Data
@TableName("review_record")
public class ReviewRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 链路追踪 ID（UUID，贯穿日志与 API 响应） */
    private String traceId;

    /** GITLAB / GITHUB */
    private String platform;

    private Long projectId;

    /** group/repo */
    private String repoPath;

    private Long mrIid;

    private String commitSha;

    private String sourceBranch;

    private String targetBranch;

    private String title;

    private String authorUsername;

    /** PENDING / REVIEWING / DONE / FAILED */
    private String status;

    /** APPROVE / NEEDS_FIX / BLOCK */
    private String conclusion;

    /** 0-100 */
    private BigDecimal confidence;

    private Integer errorCount;

    private Integer warningCount;

    private Integer infoCount;

    /** 审查耗时（毫秒） */
    private Long durationMs;

    /** webhook 触发时间 */
    private LocalDateTime triggeredAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
