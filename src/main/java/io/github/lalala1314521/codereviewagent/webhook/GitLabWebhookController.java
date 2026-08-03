package io.github.lalala1314521.codereviewagent.webhook;

import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.platform.gitlab.GitLabClient;
import io.github.lalala1314521.codereviewagent.platform.gitlab.payload.GitLabMergeRequestPayload;
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
 * GitLab Webhook 接收控制器。
 *
 * <p>对接 GitLab Repo → Settings → Webhooks → Merge request events。
 *
 * <p><b>职责收敛</b>（V3 平台化重构）：本类只做平台特有的事——
 * 验签（X-Gitlab-Token）、payload 解析与归一；
 * 归一之后的流程（幂等/落库/异步审查/评论回写/SSE 进度）由
 * {@link WebhookReviewOrchestrator} 统一编排，与 GitHub 链路零重复。
 *
 * <p>为什么必须异步？GitLab webhook 超时 10 秒，LLM 调用 3-8 秒，
 * 加上拉 diff 和网络抖动，同步链路不稳定。异步后 ACK 在 100ms 内完成，
 * GitLab 不重试，配合幂等键，重复请求也被拦截。
 */
@RestController
public class GitLabWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitLabWebhookController.class);

    /**
     * 只处理这些 action，其他（close / merge）忽略。
     */
    private static final Set<String> HANDLED_ACTIONS = Set.of("open", "update", "reopen");

    private final SignatureVerifier signatureVerifier;
    private final GitLabClient gitLabClient;
    private final WebhookReviewOrchestrator orchestrator;
    private final DebounceStore debounceStore;

    public GitLabWebhookController(SignatureVerifier signatureVerifier,
                                   GitLabClient gitLabClient,
                                   WebhookReviewOrchestrator orchestrator,
                                   DebounceStore debounceStore) {
        this.signatureVerifier = signatureVerifier;
        this.gitLabClient = gitLabClient;
        this.orchestrator = orchestrator;
        this.debounceStore = debounceStore;
    }

    @PostMapping("/webhook/gitlab")
    public ResponseEntity<String> handle(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
            @RequestBody GitLabMergeRequestPayload payload) {

        // 1. 验签（防伪造）
        if (!signatureVerifier.verifyGitLabToken(token)) {
            log.warn("webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }

        // 2. 校验 payload 类型
        if (payload == null || payload.objectAttributes() == null) {
            log.warn("webhook empty payload");
            return ResponseEntity.badRequest().body("empty payload");
        }
        if (!"merge_request".equals(payload.objectKind())) {
            log.debug("ignore non-MR webhook object_kind={}", payload.objectKind());
            return ResponseEntity.ok("ignored: not a merge_request event");
        }

        // 3. 过滤 action（只处理 open/update/reopen）
        String action = payload.objectAttributes().action();
        if (!HANDLED_ACTIONS.contains(action)) {
            log.debug("ignore MR action={} iid={}", action, payload.objectAttributes().iid());
            return ResponseEntity.ok("ignored: action=" + action);
        }

        log.info("webhook received project={} mr={} action={} commit={}",
                payload.project().id(),
                payload.objectAttributes().iid(),
                action,
                payload.objectAttributes().lastCommit() != null
                        ? payload.objectAttributes().lastCommit().id() : "unknown");

        try {
            // 4. 归一 → 去抖队列（连续 push 合并为一次审查）；Redis 故障降级直触发
            UnifiedMergeRequest mr = gitLabClient.toUnifiedMergeRequest(payload);
            try {
                debounceStore.put(mr);
                return ResponseEntity.ok("queued (debounced)");
            } catch (Exception debounceError) {
                log.warn("debounce unavailable, trigger directly: {}", debounceError.getMessage());
                return ResponseEntity.ok(orchestrator.orchestrate(mr));
            }
        } catch (Exception e) {
            log.error("webhook processing failed project={} mr={}",
                    payload.project().id(),
                    payload.objectAttributes().iid(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing failed");
        }
    }
}
