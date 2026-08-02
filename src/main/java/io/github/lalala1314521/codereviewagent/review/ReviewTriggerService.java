package io.github.lalala1314521.codereviewagent.review;

import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.persistence.ReviewRecordService;
import io.github.lalala1314521.codereviewagent.review.progress.ProgressEvent;
import io.github.lalala1314521.codereviewagent.review.progress.ReviewProgressPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 本地/demo 触发编排服务：diff 已就绪场景的统一触发入口。
 *
 * <p>与 webhook 链路的差异：不验签、不做 Redis 幂等（commitSha 含时间戳天然唯一）、
 * 评论回写跳过（没有真实 MR 可写）；<b>审查核心完全一致</b>
 * （落库 → 规则引擎 → LLM → 裁决 → SSE 进度 → findings 落库）。
 *
 * <p>使用方：{@code DemoController}（dev profile）、{@code LocalReviewController}（本地文件审查）。
 */
@Service
public class ReviewTriggerService {

    private static final Logger log = LoggerFactory.getLogger(ReviewTriggerService.class);

    private final ReviewRecordService reviewRecordService;
    private final ReviewEngine reviewEngine;
    private final ReviewProgressPublisher progressPublisher;

    public ReviewTriggerService(ReviewRecordService reviewRecordService,
                                ReviewEngine reviewEngine,
                                ReviewProgressPublisher progressPublisher) {
        this.reviewRecordService = reviewRecordService;
        this.reviewEngine = reviewEngine;
        this.progressPublisher = progressPublisher;
    }

    /**
     * 触发一次审查（diff 已填充在 mr 中）。
     *
     * @param mr            diff 已填充的统一 MR
     * @param terminalNote  终态事件附加说明（如"本地审查，未回写评论"）
     * @return review_record 主键（前端据此查队列/挂 SSE）
     */
    public Long trigger(UnifiedMergeRequest mr, String terminalNote) {
        Long recordId = reviewRecordService.createPendingRecord(mr);
        if (recordId == null) {
            throw new BizException(400, "重复触发（uk_commit 冲突），请稍后重试");
        }
        log.info("local review triggered recordId={} platform={} title={}", recordId, mr.platform(), mr.title());

        progressPublisher.publish(recordId, ProgressEvent.RECEIVED, "已接收，排队待审");
        reviewEngine.reviewWithDiffAsync(mr, recordId)
                .thenAccept(verdict -> {
                    progressPublisher.publish(recordId, ProgressEvent.PUBLISHING, "正在回写 MR 评论（无真实 MR，跳过回写）");
                    progressPublisher.publish(recordId, ProgressEvent.DONE,
                            "审查完成：" + verdict.conclusion() + "（" + terminalNote + "）");
                })
                .exceptionally(e -> {
                    log.error("local review failed recordId={}", recordId, e);
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    reviewEngine.markFailed(recordId, 0, cause.getMessage());
                    return null;
                });
        return recordId;
    }
}
