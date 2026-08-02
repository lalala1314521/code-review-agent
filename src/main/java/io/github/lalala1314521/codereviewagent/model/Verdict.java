package io.github.lalala1314521.codereviewagent.model;

import java.util.List;

/**
 * 裁决结论。
 *
 * <p>汇总所有审查发现 → 按严重度裁决出最终结论 + 置信度。
 * 对应 UI 设计稿"Agent 结论舞台"（建议合并/需修复/阻塞）+ "置信度 92%"。
 *
 * @param conclusion    结论：APPROVE / NEEDS_FIX / BLOCK
 * @param confidence    置信度：0.0~1.0（UI 建议显示"高/中/低把握"而非精确数字）
 * @param errorCount    ERROR 级发现数
 * @param warningCount  WARNING 级发现数
 * @param infoCount     INFO 级发现数
 * @param summary       一句话总结，回写 MR 时用
 * @param findings      所有审查发现（已按严重度排序）
 */
public record Verdict(
        VerdictConclusion conclusion,
        Double confidence,
        int errorCount,
        int warningCount,
        int infoCount,
        String summary,
        List<ReviewFinding> findings
) {}
