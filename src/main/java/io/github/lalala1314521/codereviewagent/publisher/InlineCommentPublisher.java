package io.github.lalala1314521.codereviewagent.publisher;

import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.model.Verdict;
import io.github.lalala1314521.codereviewagent.platform.PlatformRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 行级评论发布器：把高严重度 findings 定位到具体代码行。
 *
 * <p>规则：
 * <ul>
 *   <li>只发 ERROR/WARNING（INFO 是噪音，只进总结评论）</li>
 *   <li>每 MR 上限 {@value #MAX_INLINE} 条（防刷屏）</li>
 *   <li>先删除上次审查遗留的标记评论（防累积）</li>
 *   <li>行不在 diff hunk 内会 4xx（GitHub 422）——捕获跳过，不拖垮整体</li>
 * </ul>
 */
@Component
public class InlineCommentPublisher {

    private static final Logger log = LoggerFactory.getLogger(InlineCommentPublisher.class);
    public static final String INLINE_MARKER = "<!-- codereview-agent-inline -->";
    private static final int MAX_INLINE = 10;

    private final PlatformRouter platformRouter;

    public InlineCommentPublisher(PlatformRouter platformRouter) {
        this.platformRouter = platformRouter;
    }

    /** 发布行级评论：清理旧 → 过滤排序 → 逐个定位发送。 */
    public void publish(UnifiedMergeRequest mr, Verdict verdict) {
        if (verdict.findings() == null || verdict.findings().isEmpty()) {
            return;
        }
        // 1. 清理上次遗留
        try {
            platformRouter.deleteInlineComments(mr, INLINE_MARKER);
        } catch (Exception e) {
            log.warn("inline cleanup failed platform={} mr={}: {}", mr.platform(), mr.mrIid(), e.getMessage());
        }

        // 2. 过滤：ERROR/WARNING，行号有效；ERROR 优先
        List<ReviewFinding> candidates = verdict.findings().stream()
                .filter(f -> f.severity() == Severity.ERROR || f.severity() == Severity.WARNING)
                .filter(f -> f.lineNumber() != null && f.filePath() != null)
                .sorted(Comparator.comparing(ReviewFinding::severity)
                        .thenComparing(ReviewFinding::lineNumber))
                .limit(MAX_INLINE)
                .toList();

        // 3. 逐个发送（行不在 diff 内会 4xx，跳过继续）
        for (ReviewFinding finding : candidates) {
            try {
                platformRouter.postInlineComment(mr, finding.filePath(), finding.lineNumber(),
                        format(finding));
            } catch (Exception e) {
                log.debug("inline comment skipped (likely line not in diff) {}:{}: {}",
                        finding.filePath(), finding.lineNumber(), e.getMessage());
            }
        }
        if (!candidates.isEmpty()) {
            log.info("inline comments published platform={} mr={} count={}", mr.platform(), mr.mrIid(), candidates.size());
        }
    }

    private String format(ReviewFinding f) {
        String icon = f.severity() == Severity.ERROR ? "🔴" : "🟡";
        StringBuilder sb = new StringBuilder(INLINE_MARKER).append("\n");
        sb.append(icon).append(" **").append(f.severity()).append("** · `").append(f.ruleId()).append("`\n\n");
        sb.append(f.message());
        if (f.suggestion() != null && !f.suggestion().isBlank()) {
            sb.append("\n\n💡 ").append(f.suggestion());
        }
        return sb.toString();
    }
}
