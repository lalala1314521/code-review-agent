package io.github.lalala1314521.codereviewagent.publisher;

import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.model.Verdict;
import io.github.lalala1314521.codereviewagent.platform.PlatformRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 评论回写：把 Verdict 裁决结果包装成 MR 评论发出去。
 *
 * <p>MVP 阶段：发一条 Markdown 总结评论到 MR note。
 * V1 阶段：基于 Verdict 生成结构化总结评论（结论 + 置信度 + findings 列表）。
 * V3 阶段：评论投递经 {@link PlatformRouter} 按 mr.platform() 路由
 * （GITLAB → MR note；GITHUB → PR issue comment）。
 */
@Component
public class CommentPublisher {

    private static final Logger log = LoggerFactory.getLogger(CommentPublisher.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlatformRouter platformRouter;

    public CommentPublisher(PlatformRouter platformRouter) {
        this.platformRouter = platformRouter;
    }

    /**
     * 发布审查评论（MVP 保留，基于 Markdown 文本）。
     */
    public void publish(UnifiedMergeRequest mr, String reviewText) {
        String comment = formatLegacyComment(mr, reviewText);
        platformRouter.postComment(mr, comment);
        log.info("review comment published platform={} project={} mr={}", mr.platform(), mr.projectId(), mr.mrIid());
    }

    /**
     * 发布审查评论（V1 新增，基于 Verdict）。
     *
     * <p>生成结构化总结评论：
     * <ul>
     *   <li>结论（建议合并/需修复/阻塞）+ 置信度（高/中/低把握）</li>
     *   <li>统计（ERROR/WARNING/INFO 数量）</li>
     *   <li>findings 列表（按严重度排序，含文件/行号/建议）</li>
     *   <li>元信息（Provider、commit SHA、审查时间）</li>
     * </ul>
     */
    public void publishVerdict(UnifiedMergeRequest mr, Verdict verdict) {
        String comment = formatVerdictComment(mr, verdict);
        platformRouter.postComment(mr, comment);
        log.info("verdict comment published platform={} project={} mr={} conclusion={}",
                mr.platform(), mr.projectId(), mr.mrIid(), verdict.conclusion());
    }

    /**
     * 基于 Verdict 生成结构化 Markdown 评论。
     */
    private String formatVerdictComment(UnifiedMergeRequest mr, Verdict verdict) {
        StringBuilder sb = new StringBuilder();

        // 头部：结论 + 置信度
        sb.append("🤖 **Code Review Agent 已审查**\n\n");
        sb.append("## 总体结论\n\n");
        sb.append(getConclusionEmoji(verdict.conclusion())).append(" **")
                .append(getConclusionText(verdict.conclusion())).append("**\n\n");
        sb.append(verdict.summary()).append("\n\n");
        sb.append("**置信度**：").append(getConfidenceText(verdict.confidence()))
                .append("（").append(String.format("%.0f%%", verdict.confidence() * 100)).append("）\n\n");
        sb.append("**统计**：").append(verdict.errorCount()).append(" ERROR / ")
                .append(verdict.warningCount()).append(" WARNING / ")
                .append(verdict.infoCount()).append(" INFO\n\n");

        // findings 列表
        if (!verdict.findings().isEmpty()) {
            sb.append("## 具体问题\n\n");
            for (ReviewFinding f : verdict.findings()) {
                sb.append(getSeverityEmoji(f.severity())).append(" **")
                        .append(f.severity()).append("** · ")
                        .append(f.ruleId()).append("\n\n");
                if (f.filePath() != null) {
                    sb.append("- **文件**: `").append(f.filePath()).append("`");
                    if (f.lineNumber() != null) {
                        sb.append(":").append(f.lineNumber());
                    }
                    sb.append("\n");
                }
                sb.append("- **说明**: ").append(f.message()).append("\n");
                if (f.suggestion() != null && !f.suggestion().isBlank()) {
                    sb.append("- **建议**: ").append(f.suggestion()).append("\n");
                }
                sb.append("\n");
            }
        }

        // 元信息
        sb.append("---\n\n");
        sb.append("<sub>\n");
        sb.append("由 DeepSeek 自动审查 · MR #").append(mr.mrIid())
                .append(" · commit `").append(mr.commitSha() != null
                        ? mr.commitSha().substring(0, Math.min(8, mr.commitSha().length()))
                        : "unknown")
                .append("` · ").append(OffsetDateTime.now().format(FMT)).append("\n");
        sb.append("</sub>\n");

        return sb.toString();
    }

    /**
     * 结论 emoji。
     */
    private String getConclusionEmoji(io.github.lalala1314521.codereviewagent.model.VerdictConclusion c) {
        return switch (c) {
            case APPROVE -> "✅";
            case NEEDS_FIX -> "⚠️";
            case BLOCK -> "🚫";
        };
    }

    /**
     * 结论文案。
     */
    private String getConclusionText(io.github.lalala1314521.codereviewagent.model.VerdictConclusion c) {
        return switch (c) {
            case APPROVE -> "建议合并";
            case NEEDS_FIX -> "需修复";
            case BLOCK -> "阻塞";
        };
    }

    /**
     * 置信度文案（按 UI 设计稿建议，显示"高/中/低把握"而非精确数字）。
     */
    private String getConfidenceText(double confidence) {
        if (confidence >= 0.85) return "高把握";
        if (confidence >= 0.60) return "中等把握";
        return "低把握，建议人工复核";
    }

    /**
     * 严重度 emoji。
     */
    private String getSeverityEmoji(Severity severity) {
        return switch (severity) {
            case ERROR -> "🔴";
            case WARNING -> "🟡";
            case INFO -> "🔵";
        };
    }

    /**
     * 遗留 Markdown 评论格式（MVP 兼容）。
     */
    private String formatLegacyComment(UnifiedMergeRequest mr, String reviewText) {
        return """
                🤖 **Code Review Agent 已审查**

                %s

                ---

                <sub>
                由 DeepSeek 自动审查 · MR #%s · commit `%s` · %s
                </sub>
                """.formatted(
                reviewText,
                mr.mrIid(),
                mr.commitSha() != null ? mr.commitSha().substring(0, Math.min(8, mr.commitSha().length())) : "unknown",
                OffsetDateTime.now().format(FMT)
        );
    }
}
