package io.github.lalala1314521.codereviewagent.verdict;

import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.model.Verdict;
import io.github.lalala1314521.codereviewagent.model.VerdictConclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 裁决器：汇总所有审查发现 → 按严重度裁决出最终结论 + 置信度。
 *
 * <p>结论判定：ERROR>0 → BLOCK；WARNING>0 → NEEDS_FIX；否则 APPROVE。
 *
 * <p>置信度表达"结论本身的把握"，而非 finding 质量的平均分：
 * <ul>
 *   <li>BLOCK = 最高 ERROR 置信度（下 BLOCK 只需一个理由）</li>
 *   <li>NEEDS_FIX = WARNING 平均置信度</li>
 *   <li>APPROVE = 按规则 + LLM 双覆盖分级（0.80/0.75/0.65/0.70）</li>
 * </ul>
 * 不用平均分的原因：发现多 ≠ 把握大；且值域被压在 [LLM均值, 1.0]，无法表达"低把握"。
 */
@Component
public class VerdictDecider {

    private static final Logger log = LoggerFactory.getLogger(VerdictDecider.class);

    /**
     * 裁决。
     *
     * @param findings 所有审查发现（规则 + LLM）
     * @return 裁决结果（结论 + 置信度 + 统计 + 总结）
     */
    public Verdict decide(List<ReviewFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return new Verdict(
                    VerdictConclusion.APPROVE,
                    0.70,  // 双双无发现（MR 太小或纯文档）
                    0, 0, 0,
                    "建议合并：审查 0 个文件，未发现明显问题",
                    List.of()
            );
        }

        // 1. 统计严重度 → 结论判定
        long errorCount = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warningCount = findings.stream().filter(f -> f.severity() == Severity.WARNING).count();
        long infoCount = findings.stream().filter(f -> f.severity() == Severity.INFO).count();

        VerdictConclusion conclusion;
        if (errorCount > 0) {
            conclusion = VerdictConclusion.BLOCK;
        } else if (warningCount > 0) {
            conclusion = VerdictConclusion.NEEDS_FIX;
        } else {
            conclusion = VerdictConclusion.APPROVE;
        }

        // 2. 置信度 + 总结文案
        double confidence = calcConfidence(conclusion, findings);
        String summary = buildSummary(conclusion, errorCount, warningCount, infoCount);

        // 3. 按严重度排序（ERROR 在前，INFO 在后）
        List<ReviewFinding> sorted = findings.stream()
                .sorted(Comparator.comparingInt(f -> severityOrder(f.severity())))
                .collect(Collectors.toList());

        log.info("verdict decided conclusion={} confidence={} error={} warning={} info={}",
                conclusion, confidence, errorCount, warningCount, infoCount);

        return new Verdict(
                conclusion,
                confidence,
                (int) errorCount,
                (int) warningCount,
                (int) infoCount,
                summary,
                sorted
        );
    }

    /**
     * 置信度计算：表达"结论本身的把握"，而非 finding 质量的平均分。
     */
    private double calcConfidence(VerdictConclusion conclusion, List<ReviewFinding> findings) {
        return switch (conclusion) {
            case BLOCK:
                // 下 BLOCK 只需一个理由：取最高 ERROR 置信度
                yield findings.stream()
                        .filter(f -> f.severity() == Severity.ERROR)
                        .mapToDouble(f -> f.confidence() != null ? f.confidence() : 1.0)
                        .max().orElse(0.7);

            case NEEDS_FIX:
                // 多个相关问题取平均
                yield findings.stream()
                        .filter(f -> f.severity() == Severity.WARNING)
                        .mapToDouble(f -> f.confidence() != null ? f.confidence() : 1.0)
                        .average().orElse(0.7);

            case APPROVE:
                // 双覆盖分级：规则 + LLM 都确认 → 最高把握
                boolean hasRuleCoverage = findings.stream().anyMatch(f -> "RULE".equals(f.source()));
                boolean hasLlmCoverage = findings.stream().anyMatch(f -> "LLM".equals(f.source()));
                if (hasRuleCoverage && hasLlmCoverage) yield 0.80;   // 双重确认
                if (hasRuleCoverage) yield 0.75;                     // 规则覆盖，LLM 未发言
                if (hasLlmCoverage) yield 0.65;                      // 仅 LLM 确认
                yield 0.70;                                          // 双双无发现（MR 太小或纯文档）
        };
    }

    /**
     * 总结文案模板。
     */
    private String buildSummary(VerdictConclusion conclusion, long errorCount, long warningCount, long infoCount) {
        return switch (conclusion) {
            case APPROVE:
                yield String.format("建议合并：审查发现 %d 条建议（INFO），未发现严重问题", infoCount);
            case NEEDS_FIX:
                yield String.format("需修复 %d 处问题后合并（%d ERROR / %d WARNING / %d INFO）",
                        warningCount, errorCount, warningCount, infoCount);
            case BLOCK:
                yield String.format("阻塞：发现 %d 个严重问题（安全/数据风险），请修复后重新提交（%d ERROR / %d WARNING / %d INFO）",
                        errorCount, errorCount, warningCount, infoCount);
        };
    }

    /**
     * 严重度排序权重（ERROR=0, WARNING=1, INFO=2）。
     */
    private int severityOrder(Severity severity) {
        return switch (severity) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }
}
