package io.github.lalala1314521.codereviewagent.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * push 去抖调度器：定期扫描去抖存储，触发静默到期的审查。
 *
 * <p>窗口 20s：同一 MR 20s 内没有新 push 才审——连续 push 只审最后一个 commit。
 */
@Component
public class DebounceScheduler {

    private static final Logger log = LoggerFactory.getLogger(DebounceScheduler.class);
    private static final long QUIET_WINDOW_MS = 20_000;

    private final DebounceStore debounceStore;
    private final WebhookReviewOrchestrator orchestrator;

    public DebounceScheduler(DebounceStore debounceStore, WebhookReviewOrchestrator orchestrator) {
        this.debounceStore = debounceStore;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void fire() {
        List<DebounceStore.PendingEntry> ready = debounceStore.pollQuiet(QUIET_WINDOW_MS);
        for (DebounceStore.PendingEntry entry : ready) {
            log.info("debounce fire platform={} project={} mr={} sha={}",
                    entry.mr().platform(), entry.mr().projectId(), entry.mr().mrIid(), entry.mr().commitSha());
            debounceStore.remove(entry.field());
            orchestrator.orchestrate(entry.mr());
        }
    }
}
