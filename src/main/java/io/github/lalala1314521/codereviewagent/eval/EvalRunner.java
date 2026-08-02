package io.github.lalala1314521.codereviewagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lalala1314521.codereviewagent.eval.dto.EvalCase;
import io.github.lalala1314521.codereviewagent.eval.dto.EvalExpected;
import io.github.lalala1314521.codereviewagent.eval.dto.LlmFindings;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.model.Verdict;
import io.github.lalala1314521.codereviewagent.review.ReviewEngine;
import io.github.lalala1314521.codereviewagent.review.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 评测运行器：加载用例 → 全链路审查（规则 + LLM + 合并 + Verdict）→ 匹配打分 → 输出 markdown 报告。
 *
 * <p>用法：{@code new EvalRunner(llmClient, objectMapper, reviewEngine).run()}，输出到 eval/reports/。
 *
 * <p>V2 起评测对象从"LLM 裸输出"升级为"系统真实行为"（{@link ReviewEngine#reviewWithDiff}）。
 * 匹配规则：同 filePath、行号差 ≤ 2、severity 一致或差一级、message 含 mustMention 关键词。
 */
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ReviewEngine reviewEngine;

    private final Path casesDir;
    private final Path expectedDir;
    private final Path reportsDir;

    public EvalRunner(LlmClient llmClient, ObjectMapper objectMapper, ReviewEngine reviewEngine) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.reviewEngine = reviewEngine;
        this.casesDir = Paths.get("eval/cases");
        this.expectedDir = Paths.get("eval/expected");
        this.reportsDir = Paths.get("eval/reports");
    }

    /**
     * 跑一次完整评测。
     *
     * @return 评测报告内容（markdown）
     */
    public String run() {
        List<EvalCase> cases = loadCases();
        List<EvalExpected> expecteds = loadExpecteds();

        if (cases.isEmpty()) {
            log.warn("no eval cases found in {}", casesDir.toAbsolutePath());
            return "No cases found.";
        }

        log.info("eval start cases={} expecteds={} provider={}",
                cases.size(), expecteds.size(), llmClient.providerName());

        List<CaseResult> results = new ArrayList<>();
        long totalStartMs = System.currentTimeMillis();

        for (EvalCase c : cases) {
            EvalExpected expected = expecteds.stream()
                    .filter(e -> e.id().equals(c.id()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("missing expected for " + c.id()));

            CaseResult r = evalCase(c, expected);
            results.add(r);
            log.info("case {} done recall={}/{} precision={}/{} verdict={}",
                    c.id(), r.recallHit, r.expectedCount, r.precisionHit, r.actualCount, r.verdictMatch);
        }

        long totalDurationMs = System.currentTimeMillis() - totalStartMs;
        String report = buildReport(results, totalDurationMs);
        writeReport(report);
        return report;
    }

    // ===== 单用例评测 =====

    private CaseResult evalCase(EvalCase c, EvalExpected e) {
        long startMs = System.currentTimeMillis();

        // 1. 全链路审查（规则 → LLM → 合并去重 → Verdict，与生产链路一致）
        UnifiedMergeRequest mr = new UnifiedMergeRequest(
                "EVAL", 0L, "eval/cases", 0L, "eval",
                null, null, c.description(), null, "eval", "eval", null, c.diff());
        Verdict verdict;
        try {
            // 裸 hunk 用例无文件头，用用例声明的 filePath 兜底
            verdict = reviewEngine.reviewWithDiff(mr, c.filePath());
        } catch (Exception ex) {
            log.error("review failed for case {}", c.id(), ex);
            return CaseResult.failed(c, e, System.currentTimeMillis() - startMs);
        }

        // 2. 补 filePath（裸 hunk 的规则 finding 无文件头，补上再匹配）
        List<LlmFindings.Finding> actual = verdict.findings().stream()
                .map(f -> new LlmFindings.Finding(
                        f.filePath() != null ? f.filePath() : c.filePath(),
                        f.lineNumber(),
                        f.severity().name(),
                        f.ruleId(),
                        f.message(),
                        f.suggestion()))
                .collect(Collectors.toList());

        // 3. 匹配打分
        List<EvalExpected.ExpectedFinding> expected = e.expectedFindings() != null
                ? e.expectedFindings()
                : List.of();

        int recallHit = 0;
        List<EvalExpected.ExpectedFinding> missed = new ArrayList<>();
        for (EvalExpected.ExpectedFinding exp : expected) {
            if (matchFinding(exp, actual, c.filePath())) {
                recallHit++;
            } else {
                missed.add(exp);
            }
        }

        int precisionHit = 0;
        List<LlmFindings.Finding> falsePositives = new ArrayList<>();
        for (LlmFindings.Finding act : actual) {
            if (matchActual(act, expected, c.filePath())) {
                precisionHit++;
            } else {
                falsePositives.add(act);
            }
        }

        // 4. Verdict 用系统真实裁决结论
        String actualVerdict = verdict.conclusion().name();
        boolean verdictMatch = actualVerdict.equals(e.expectedConclusion());

        long durationMs = System.currentTimeMillis() - startMs;

        return new CaseResult(
                c, e, actual,
                expected.size(), actual.size(),
                recallHit, precisionHit,
                actualVerdict, e.expectedConclusion(), verdictMatch,
                missed, falsePositives,
                durationMs, null, null
        );
    }

    // ===== 匹配逻辑 =====

    /**
     * 判断期望的 finding 是否被 LLM 实际输出命中（recall 用）。
     */
    private boolean matchFinding(EvalExpected.ExpectedFinding exp, List<LlmFindings.Finding> actual, String filePath) {
        return actual.stream().anyMatch(act -> isMatch(exp, act, filePath));
    }

    /**
     * 判断 LLM 实际输出是否在期望里（precision 用）。
     */
    private boolean matchActual(LlmFindings.Finding act, List<EvalExpected.ExpectedFinding> expected, String filePath) {
        return expected.stream().anyMatch(exp -> isMatch(exp, act, filePath));
    }

    /**
     * 单条匹配：filePath + 行号 ±2 + severity 差一级内 + mustMention 关键词。
     */
    private boolean isMatch(EvalExpected.ExpectedFinding exp, LlmFindings.Finding act, String filePath) {
        if (!filePath.equals(act.filePath())) return false;

        // 行号差 ≤ 2（exp.lineNumber 为 null 表示文件级）
        if (exp.lineNumber() != null && act.lineNumber() != null) {
            if (Math.abs(exp.lineNumber() - act.lineNumber()) > 2) return false;
        } else if (exp.lineNumber() != null || act.lineNumber() != null) {
            return false;   // 一个为 null 一个不为 null → 不匹配
        }

        if (!severityClose(exp.severity(), act.severity())) return false;

        // message 含 mustMention 任一关键词（大小写不敏感）
        if (exp.mustMention() != null && !exp.mustMention().isEmpty()) {
            String msg = act.message() != null ? act.message().toLowerCase() : "";
            boolean mentionHit = exp.mustMention().stream()
                    .anyMatch(k -> msg.contains(k.toLowerCase()));
            if (!mentionHit) return false;
        }

        return true;
    }

    private boolean severityClose(String exp, String act) {
        if (exp == null || act == null) return false;
        if (exp.equals(act)) return true;
        // 差一级：ERROR ↔ WARNING 或 WARNING ↔ INFO
        return ("ERROR".equals(exp) && "WARNING".equals(act))
                || ("WARNING".equals(exp) && "ERROR".equals(act))
                || ("WARNING".equals(exp) && "INFO".equals(act))
                || ("INFO".equals(exp) && "WARNING".equals(act));
    }

    // ===== 用例加载 =====

    private List<EvalCase> loadCases() {
        if (!Files.exists(casesDir)) return List.of();
        try (Stream<Path> s = Files.list(casesDir)) {
            return s.filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .map(p -> {
                        try {
                            return objectMapper.readValue(p.toFile(), EvalCase.class);
                        } catch (IOException e) {
                            log.error("load case failed: {}", p, e);
                            return null;
                        }
                    })
                    .filter(c -> c != null)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("list cases failed", e);
            return List.of();
        }
    }

    private List<EvalExpected> loadExpecteds() {
        if (!Files.exists(expectedDir)) return List.of();
        try (Stream<Path> s = Files.list(expectedDir)) {
            return s.filter(p -> p.toString().endsWith(".expected.json"))
                    .sorted()
                    .map(p -> {
                        try {
                            return objectMapper.readValue(p.toFile(), EvalExpected.class);
                        } catch (IOException e) {
                            log.error("load expected failed: {}", p, e);
                            return null;
                        }
                    })
                    .filter(e -> e != null)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("list expecteds failed", e);
            return List.of();
        }
    }

    // ===== 报告生成 =====

    private String buildReport(List<CaseResult> results, long totalDurationMs) {
        int totalCases = results.size();
        int totalExpected = results.stream().mapToInt(r -> r.expectedCount).sum();
        int totalRecallHit = results.stream().mapToInt(r -> r.recallHit).sum();
        int totalActual = results.stream().mapToInt(r -> r.actualCount).sum();
        int totalPrecisionHit = results.stream().mapToInt(r -> r.precisionHit).sum();
        int verdictMatchCount = (int) results.stream().filter(r -> r.verdictMatch).count();

        double recall = totalExpected == 0 ? 0.0 : (double) totalRecallHit / totalExpected;
        double precision = totalActual == 0 ? 0.0 : (double) totalPrecisionHit / totalActual;
        double verdictRate = (double) verdictMatchCount / totalCases;
        double avgDuration = results.stream().mapToLong(r -> r.durationMs).average().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append("# 评测报告 - ").append(java.time.LocalDate.now())
                .append(" - ").append(llmClient.providerName()).append("\n\n");

        sb.append("## 总体指标\n\n");
        sb.append("| 指标 | 值 | 目标 |\n|---|---|---|\n");
        sb.append(String.format("| 总用例数 | %d | - |\n", totalCases));
        sb.append(String.format("| **Recall** | %.2f (%d/%d) | ≥ 0.70 |\n", recall, totalRecallHit, totalExpected));
        sb.append(String.format("| **Precision** | %.2f (%d/%d) | ≥ 0.60 |\n", precision, totalPrecisionHit, totalActual));
        sb.append(String.format("| **Verdict 一致率** | %.2f (%d/%d) | ≥ 0.80 |\n", verdictRate, verdictMatchCount, totalCases));
        sb.append(String.format("| 平均耗时 | %.1fs | ≤ 3s |\n", avgDuration / 1000));
        sb.append(String.format("| 总耗时 | %.1fs | - |\n", totalDurationMs / 1000.0));

        // 未召回的用例
        List<CaseResult> withMissed = results.stream().filter(r -> !r.missed.isEmpty()).collect(Collectors.toList());
        if (!withMissed.isEmpty()) {
            sb.append("\n## 未召回的用例\n\n");
            for (CaseResult r : withMissed) {
                sb.append(String.format("- **%s** (%s)\n", r.evalCase.id(), r.evalCase.description()));
                for (EvalExpected.ExpectedFinding exp : r.missed) {
                    sb.append(String.format("  - 期望: %s @ 行 %d (%s)\n", exp.category(), exp.lineNumber(), exp.severity()));
                }
            }
        }

        // 误报（false positives）
        List<CaseResult> withFp = results.stream().filter(r -> !r.falsePositives.isEmpty()).collect(Collectors.toList());
        if (!withFp.isEmpty()) {
            sb.append("\n## 误报（LLM 多报的）\n\n");
            for (CaseResult r : withFp) {
                sb.append(String.format("- **%s** (%s)\n", r.evalCase.id(), r.evalCase.description()));
                for (LlmFindings.Finding fp : r.falsePositives) {
                    sb.append(String.format("  - %s @ %s:%d (%s): %s\n",
                            fp.ruleId(), fp.filePath(), fp.lineNumber(), fp.severity(), fp.message()));
                }
            }
        }

        // Verdict 不一致
        List<CaseResult> verdictMismatch = results.stream().filter(r -> !r.verdictMatch).collect(Collectors.toList());
        if (!verdictMismatch.isEmpty()) {
            sb.append("\n## Verdict 不一致\n\n");
            for (CaseResult r : verdictMismatch) {
                sb.append(String.format("- **%s**: 期望 %s，实际 %s\n",
                        r.evalCase.id(), r.expectedVerdict, r.actualVerdict));
            }
        }

        // 单用例明细
        sb.append("\n## 单用例明细\n\n");
        sb.append("| 用例 | 期望数 | 实际数 | Recall | Precision | Verdict | 耗时 |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (CaseResult r : results) {
            double caseRecall = r.expectedCount == 0 ? 1.0 : (double) r.recallHit / r.expectedCount;
            double casePrecision = r.actualCount == 0 ? 1.0 : (double) r.precisionHit / r.actualCount;
            sb.append(String.format("| %s | %d | %d | %.2f | %.2f | %s | %.1fs |\n",
                    r.evalCase.id(),
                    r.expectedCount, r.actualCount,
                    caseRecall, casePrecision,
                    r.verdictMatch ? "✓" : "✗",
                    r.durationMs / 1000.0));
        }

        return sb.toString();
    }

    private void writeReport(String report) {
        try {
            Files.createDirectories(reportsDir);
            String filename = java.time.LocalDate.now() + "_" + llmClient.providerName() + ".md";
            Path reportPath = reportsDir.resolve(filename);
            Files.writeString(reportPath, report);
            log.info("eval report written to {}", reportPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("write report failed", e);
        }
    }

    // ===== 内部结果类 =====

    private record CaseResult(
            EvalCase evalCase,
            EvalExpected evalExpected,
            List<LlmFindings.Finding> actualFindings,
            int expectedCount,
            int actualCount,
            int recallHit,
            int precisionHit,
            String actualVerdict,
            String expectedVerdict,
            boolean verdictMatch,
            List<EvalExpected.ExpectedFinding> missed,
            List<LlmFindings.Finding> falsePositives,
            long durationMs,
            String rawResponse,
            String error
    ) {
        static CaseResult failed(EvalCase c, EvalExpected e, long durationMs) {
            return new CaseResult(
                    c, e, List.of(),
                    e.expectedFindings() != null ? e.expectedFindings().size() : 0,
                    0, 0, 0,
                    "FAILED", e.expectedConclusion(), false,
                    e.expectedFindings() != null ? e.expectedFindings() : List.of(),
                    List.of(),
                    durationMs, null, "LLM call failed"
            );
        }
    }
}
