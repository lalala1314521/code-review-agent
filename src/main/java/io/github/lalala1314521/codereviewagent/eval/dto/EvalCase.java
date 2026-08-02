package io.github.lalala1314521.codereviewagent.eval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 评测用例。
 *
 * <p>对应 eval/cases/case_xxx.json 的结构。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalCase(
        String id,
        String language,
        String description,
        String filePath,
        String diff
) {}
