package io.github.lalala1314521.codereviewagent;

import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.model.Verdict;
import io.github.lalala1314521.codereviewagent.model.VerdictConclusion;
import io.github.lalala1314521.codereviewagent.verdict.VerdictDecider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verdict 裁决测试：ERROR→BLOCK / WARNING→NEEDS_FIX / 无发现→APPROVE。
 */
class VerdictDeciderTest {

    private final VerdictDecider decider = new VerdictDecider();

    private ReviewFinding finding(Severity severity) {
        return new ReviewFinding("Test.java", 1, severity, "test_rule",
                "msg", null, "RULE", 1.0);
    }

    @Test
    void emptyFindingsApprove() {
        Verdict v = decider.decide(List.of());
        assertEquals(VerdictConclusion.APPROVE, v.conclusion());
    }

    @Test
    void errorBlocks() {
        Verdict v = decider.decide(List.of(finding(Severity.ERROR)));
        assertEquals(VerdictConclusion.BLOCK, v.conclusion());
    }

    @Test
    void warningNeedsFix() {
        Verdict v = decider.decide(List.of(finding(Severity.WARNING)));
        assertEquals(VerdictConclusion.NEEDS_FIX, v.conclusion());
    }

    @Test
    void infoOnlyApprove() {
        Verdict v = decider.decide(List.of(finding(Severity.INFO)));
        assertEquals(VerdictConclusion.APPROVE, v.conclusion());
    }

    @Test
    void mixedSeverityTakesHighest() {
        Verdict v = decider.decide(List.of(finding(Severity.WARNING), finding(Severity.ERROR)));
        assertEquals(VerdictConclusion.BLOCK, v.conclusion());
        assertEquals(1, v.errorCount());
        assertEquals(1, v.warningCount());
    }
}
