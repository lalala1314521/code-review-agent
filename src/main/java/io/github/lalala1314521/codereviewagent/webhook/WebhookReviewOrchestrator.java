package io.github.lalala1314521.codereviewagent.webhook;

import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.persistence.ReviewRecordService;
import io.github.lalala1314521.codereviewagent.publisher.CommentPublisher;
import io.github.lalala1314521.codereviewagent.publisher.InlineCommentPublisher;
import io.github.lalala1314521.codereviewagent.review.ReviewEngine;
import io.github.lalala1314521.codereviewagent.review.progress.ProgressEvent;
import io.github.lalala1314521.codereviewagent.review.progress.ReviewProgressPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Webhook 审查编排器：平台无关的"归一之后"公共流程。
 *
 * <p><b>为什么提取</b>：GitLab/GitHub webhook 的差异只在"验签 + payload 归一"，
 * 之后的流程（幂等 → 落库 → 异步审查 → 回写评论 + SSE 进度）完全一致。
 * 提取后各平台 Controller 只做两件事：验签、归一，然后调 {@link #orchestrate}——
 * 加新平台 = 一个新 Controller（~60 行）+ 一个新 Client，编排零改动。
 *
 * <p>返回字符串是 ACK 消息（便于日志/调试区分跳过原因）。
 */
@Component
public class WebhookReviewOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WebhookReviewOrchestrator.class);

    private final IdempotentGuard idempotentGuard;
    private final ReviewRecordService reviewRecordService;
    private final ReviewEngine reviewEngine;
    private final CommentPublisher commentPublisher;
    private final InlineCommentPublisher inlineCommentPublisher;
    private final ReviewProgressPublisher progressPublisher;

    public WebhookReviewOrchestrator(IdempotentGuard idempotentGuard,
                                     ReviewRecordService reviewRecordService,
                                     ReviewEngine reviewEngine,
                                     CommentPublisher commentPublisher,
                                     InlineCommentPublisher inlineCommentPublisher,
                                     ReviewProgressPublisher progressPublisher) {
        this.idempotentGuard = idempotentGuard;
        this.reviewRecordService = reviewRecordService;
        this.reviewEngine = reviewEngine;
        this.commentPublisher = commentPublisher;
        this.inlineCommentPublisher = inlineCommentPublisher;
        this.progressPublisher = progressPublisher;
    }

    /**
     * 编排一次 MR 审查（幂等 → 落库 → 异步审查 → 评论回写 + SSE 进度）。
     *
     * @return ACK 消息（"review queued" 或跳过原因）
     */
    public String orchestrate(UnifiedMergeRequest mr) {
        // 1. 幂等校验（Redis SETNX，防同 commit 重复审）
        if (!idempotentGuard.tryAcquire(mr)) {
            return "duplicate request, skipped";
        }

        // 2. 落 PENDING 记录（uk_commit 兜底；落库失败仅告警不阻断）
        Long recordId = null;
        try {
            recordId = reviewRecordService.createPendingRecord(mr);
            if (recordId == null) {
                return "duplicate request (db unique key), skipped";
            }
        } catch (Exception e) {
            log.error("create pending record failed, continue without persistence platform={} project={} mr={}: {}",
                    mr.platform(), mr.projectId(), mr.mrIid(), e.getMessage());
        }

        // 3. 异步审查 + 评论回写 + SSE 进度
        progressPublisher.publish(recordId, ProgressEvent.RECEIVED, "已接收，排队待审");
        final Long finalRecordId = recordId;
        final long triggerNanos = System.nanoTime();
        reviewEngine.reviewAsync(mr, recordId)
                .thenAccept(verdict -> {
                    progressPublisher.publish(finalRecordId, ProgressEvent.PUBLISHING, "正在回写 MR 评论");
                    try {
                        commentPublisher.publishVerdict(mr, verdict);
                        inlineCommentPublisher.publish(mr, verdict);
                        progressPublisher.publish(finalRecordId, ProgressEvent.DONE, "审查完成，评论已回写");
                        log.info("async review completed platform={} project={} mr={} conclusion={}",
                                mr.platform(), mr.projectId(), mr.mrIid(), verdict.conclusion());
                    } catch (Exception e) {
                        // 评论回写失败 ≠ 审查失败：结果已落库，终态仍发 DONE
                        progressPublisher.publish(finalRecordId, ProgressEvent.DONE,
                                "审查完成（评论回写失败，详见服务日志）");
                        log.error("async publish failed platform={} project={} mr={}",
                                mr.platform(), mr.projectId(), mr.mrIid(), e);
                    }
                })
                .exceptionally(e -> {
                    log.error("async review failed platform={} project={} mr={}",
                            mr.platform(), mr.projectId(), mr.mrIid(), e);
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    reviewEngine.markFailed(finalRecordId, (System.nanoTime() - triggerNanos) / 1_000_000,
                            cause.getMessage());
                    return null;
                });

        return "review queued";
    }
}
