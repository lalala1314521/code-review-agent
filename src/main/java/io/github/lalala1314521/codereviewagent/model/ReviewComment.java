package io.github.lalala1314521.codereviewagent.model;

/**
 * 统一审查意见（引擎输出）。
 *
 * <p>MVP 阶段：DeepSeek 返回的 JSON 数组解析成 List<ReviewComment>。
 * V1 起：规则引擎也会产出此对象；V2 起：落库为 review_finding。
 *
 * @param filePath    评论挂载文件
 * @param lineNumber  行号；null 表示 MR 级总结
 * @param severity    ERROR / WARNING / INFO；MVP 阶段 LLM 输出
 * @param ruleId      命中规则 ID；LLM 生成填 "llm_*"
 * @param message     人类可读意见
 * @param suggestion  可选：建议代码片段
 */
public record ReviewComment(
        String filePath,
        Integer lineNumber,
        String severity,
        String ruleId,
        String message,
        String suggestion
) {}
