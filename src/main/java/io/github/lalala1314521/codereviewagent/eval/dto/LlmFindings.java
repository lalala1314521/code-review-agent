package io.github.lalala1314521.codereviewagent.eval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * LLM 输出的结构化审查结果。
 *
 * <p>评测时要求 LLM 输出 JSON 数组，每项对应一条 ReviewComment。
 * 兼容宽松字段：找不到的字段用 null。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmFindings(
        List<Finding> findings
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Finding(
            String filePath,
            Integer lineNumber,
            String severity,
            String ruleId,
            String message,
            String suggestion
    ) {}
}
