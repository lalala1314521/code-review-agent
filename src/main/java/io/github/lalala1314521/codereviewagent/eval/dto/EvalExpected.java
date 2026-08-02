package io.github.lalala1314521.codereviewagent.eval.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 人工标注的标准答案。
 *
 * <p>对应 eval/expected/case_xxx.expected.json 的结构。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalExpected(
        String id,
        List<ExpectedFinding> expectedFindings,
        String expectedConclusion
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExpectedFinding(
            Integer lineNumber,
            String severity,
            String category,
            List<String> mustMention
    ) {}
}
