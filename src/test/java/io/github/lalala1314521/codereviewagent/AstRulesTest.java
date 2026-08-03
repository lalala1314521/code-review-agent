package io.github.lalala1314521.codereviewagent;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.DiffHunk;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.model.LineType;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.review.rule.RuleContext;
import io.github.lalala1314521.codereviewagent.review.rule.ast.HighComplexityRule;
import io.github.lalala1314521.codereviewagent.review.rule.ast.UnusedImportRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AST 规则测试：真实 JavaParser 解析 + 完整规则调用链。
 */
class AstRulesTest {

    private static final String SOURCE = """
            package com.demo;
            import java.util.List;
            import java.util.Map;
            import java.io.File;
            public class AstDemo {
                public Map<String, String> ok(Map<String, String> m) { return m; }
                public int complex(int a, int b, int c) {
                    int r = 0;
                    if (a > 1) { r++; }
                    if (a > 2) { r++; }
                    if (a > 3) { r++; }
                    if (a > 4) { r++; }
                    if (a > 5) { r++; }
                    if (a > 6 && b > 1) { r++; }
                    return r + c;
                }
            }
            """;

    private RuleContext ctx(Map<String, String> params) {
        AtomicInteger line = new AtomicInteger(0);
        List<HunkLine> lines = SOURCE.lines()
                .map(l -> new HunkLine(LineType.ADD, -1, line.incrementAndGet(), l))
                .toList();
        DiffHunk hunk = new DiffHunk("src/com/demo/AstDemo.java", "src/com/demo/AstDemo.java",
                0, 0, 1, lines.size(), lines);
        DiffFile file = new DiffFile("src/com/demo/AstDemo.java", "src/com/demo/AstDemo.java",
                false, false, true, "java", List.of(hunk));
        UnifiedMergeRequest mr = new UnifiedMergeRequest("LOCAL", 0L, "local-project", 1L,
                "test", "main", "main", "t", null, "u", "u", null, "");
        return new RuleContext(params, List.of(file), mr, (f, m) -> SOURCE);
    }

    @Test
    void unusedImportHitsOnlyUnused() {
        RuleContext ctx = ctx(Map.of());
        List<ReviewFinding> findings = new UnusedImportRule().apply(ctx.allFiles().get(0), ctx);
        // List/File 未使用命中；Map 被使用不报
        assertEquals(2, findings.size());
        assertTrue(findings.stream().anyMatch(f -> f.message().contains("java.util.List")));
        assertTrue(findings.stream().anyMatch(f -> f.message().contains("java.io.File")));
        assertTrue(findings.stream().noneMatch(f -> f.message().contains("java.util.Map")));
    }

    @Test
    void highComplexityOverThreshold() {
        RuleContext ctx = ctx(Map.of("maxComplexity", "5"));
        List<ReviewFinding> findings = new HighComplexityRule().apply(ctx.allFiles().get(0), ctx);
        // complex 方法（6 if + 1 && = 8）命中；ok 方法（1）不命中
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).message().contains("complex"));
    }

    @Test
    void highComplexityBelowThresholdSilent() {
        RuleContext ctx = ctx(Map.of("maxComplexity", "20"));
        List<ReviewFinding> findings = new HighComplexityRule().apply(ctx.allFiles().get(0), ctx);
        assertTrue(findings.isEmpty());
    }
}
