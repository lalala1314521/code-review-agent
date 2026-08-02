package io.github.lalala1314521.codereviewagent.api;

import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.review.ReviewTriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开发环境演示触发器（<b>仅 dev profile 生效</b>，生产环境不注册此 Bean）。
 *
 * <p>与 {@code LocalReviewController} 的差异：本端点直接收 diff 原文、固定 demo 元数据，
 * 供 curl 快速联调；本地文件审查（产品端点，支持源码文件包装）走 /api/v1/reviews/local。
 * 两者共用 {@link ReviewTriggerService} 编排。
 */
@RestController
@RequestMapping("/api/v1/demo")
@Profile("dev")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final ReviewTriggerService triggerService;

    public DemoController(ReviewTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    /**
     * 触发一次演示审查（走真实 reviewWithDiff 全链路）。
     *
     * @return recordId，前端据此查队列 / 挂 SSE
     */
    @PostMapping("/review")
    public ApiResponse<Map<String, Object>> trigger(@RequestBody DemoReviewRequest req) {
        if (req == null || req.diff() == null || req.diff().isBlank()) {
            throw new BizException(400, "diff 不能为空");
        }

        // 时间戳当 commitSha/mrIid：天然幂等，可反复触发
        long ts = System.currentTimeMillis();
        UnifiedMergeRequest mr = new UnifiedMergeRequest(
                "GITLAB", 100L,
                req.repoPath() != null ? req.repoPath() : "demo/sse-showcase",
                ts % 100000,
                "demo-" + ts,
                "feat/demo", "main",
                req.title() != null ? req.title() : "feat: 演示审查",
                null, "demo", req.author() != null ? req.author() : "demo",
                null, req.diff()
        );

        Long recordId = triggerService.trigger(mr, "demo 环境，未回写评论");
        log.info("demo review triggered recordId={}", recordId);
        return ApiResponse.ok(Map.of("recordId", recordId));
    }

    /**
     * 演示触发请求体。
     */
    public record DemoReviewRequest(String repoPath, String title, String author, String diff) {}
}
