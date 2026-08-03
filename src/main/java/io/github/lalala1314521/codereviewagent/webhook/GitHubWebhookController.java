package io.github.lalala1314521.codereviewagent.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.platform.github.GitHubClient;
import io.github.lalala1314521.codereviewagent.platform.github.payload.GitHubPullRequestPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * GitHub Webhook 接收控制器。
 *
 * <p>对接 GitHub Repo → Settings → Webhooks → Pull requests 事件。
 *
 * <p><b>与 GitLab 控制器的对称设计</b>：职责同样收敛——验签、归一，
 * 之后交给 {@link WebhookReviewOrchestrator} 统一编排。两个控制器加起来
 * 覆盖两个平台，编排零重复。
 *
 * <p><b>验签的关键细节</b>：HMAC-SHA256 是对<b>原始请求体</b>签名，
 * 所以这里必须 {@code @RequestBody String} 先收原文、验签通过后再手动反序列化——
 * 直接收 DTO 会让 Spring 先消费掉原文，签名永远对不上。
 */
@RestController
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    /** 只处理这些 PR action，其他（closed / labeled / assigned ...）忽略 */
    private static final Set<String> HANDLED_ACTIONS = Set.of("opened", "synchronize", "reopened");

    private final GitHubSignatureVerifier signatureVerifier;
    private final GitHubClient gitHubClient;
    private final WebhookReviewOrchestrator orchestrator;
    private final DebounceStore debounceStore;
    private final ObjectMapper objectMapper;

    public GitHubWebhookController(GitHubSignatureVerifier signatureVerifier,
                                   GitHubClient gitHubClient,
                                   WebhookReviewOrchestrator orchestrator,
                                   DebounceStore debounceStore,
                                   ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.gitHubClient = gitHubClient;
        this.orchestrator = orchestrator;
        this.debounceStore = debounceStore;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook/github")
    public ResponseEntity<String> handle(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestBody String rawBody) {

        // 1. 验签（HMAC-SHA256 over 原始请求体）
        if (!signatureVerifier.verify(rawBody, signature)) {
            log.warn("github webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }

        // 2. 事件过滤：只处理 pull_request；ping 回 pong 便于配置页验证
        if ("ping".equals(eventType)) {
            return ResponseEntity.ok("pong");
        }
        if (!"pull_request".equals(eventType)) {
            return ResponseEntity.ok("ignored: event=" + eventType);
        }

        try {
            // 3. 反序列化 + 过滤 action
            GitHubPullRequestPayload payload = objectMapper.readValue(rawBody, GitHubPullRequestPayload.class);
            if (payload.pullRequest() == null) {
                return ResponseEntity.badRequest().body("empty pull_request payload");
            }
            String action = payload.action();
            if (!HANDLED_ACTIONS.contains(action)) {
                log.debug("ignore PR action={} number={}", action, payload.number());
                return ResponseEntity.ok("ignored: action=" + action);
            }

            log.info("github webhook received repo={} pr={} action={} sha={}",
                    payload.repository() != null ? payload.repository().fullName() : "unknown",
                    payload.number(), action,
                    payload.pullRequest().head() != null ? payload.pullRequest().head().sha() : "unknown");

            // 4. 归一 → 去抖队列（连续 push 合并为一次审查）；Redis 故障降级直触发
            UnifiedMergeRequest mr = gitHubClient.toUnifiedMergeRequest(payload);
            try {
                debounceStore.put(mr);
                return ResponseEntity.ok("queued (debounced)");
            } catch (Exception debounceError) {
                log.warn("debounce unavailable, trigger directly: {}", debounceError.getMessage());
                return ResponseEntity.ok(orchestrator.orchestrate(mr));
            }
        } catch (Exception e) {
            log.error("github webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing failed");
        }
    }
}
