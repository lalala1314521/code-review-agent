package io.github.lalala1314521.codereviewagent.model;

import java.util.List;

/**
 * 审查发现（结构化输出，规则引擎与 LLM 共用；落库为 review_finding）。
 *
 * @param filePath    评论挂载文件
 * @param lineNumber  行号；null 表示文件级问题
 * @param severity    ERROR / WARNING / INFO
 * @param ruleId      命中规则 ID；LLM 生成填 "llm_*"
 * @param message     人类可读意见
 * @param suggestion  可选：建议代码片段
 * @param source      RULE / LLM
 * @param confidence  0.0~1.0；规则命中固定 1.0，LLM 按返回的置信度
 */
public record ReviewFinding(
        String filePath,
        Integer lineNumber,
        Severity severity,
        String ruleId,
        String message,
        String suggestion,
        String source,
        Double confidence
) {}
