package io.github.lalala1314521.codereviewagent.review.rule.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.model.LineType;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.review.rule.RuleContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 未使用的 import（WARNING / java）。
 *
 * <p>AST 分析：统计每个 import 简单名在源码中的引用次数（排除 import 行自身与包名）。
 * 两点防误报：
 * <ul>
 *   <li>同简单名的多个 import（如 java.util.Date / java.sql.Date）冲突 → 跳过不报</li>
 *   <li>只报"本次 diff 新增的 import"——历史遗留的未使用 import 不属于本次变更</li>
 * </ul>
 */
@Component
public class UnusedImportRule extends AbstractAstRule {

    @Override
    public String ruleId() {
        return "unused_import";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public String applicableLanguage() {
        return "java";
    }

    @Override
    public List<ReviewFinding> apply(DiffFile file, RuleContext ctx) {
        CompilationUnit cu = parse(file, ctx);
        if (cu == null || cu.getImports().isEmpty()) {
            return List.of();
        }

        // 本次 diff 新增的 import 行号
        Map<Integer, String> addedImportLines = file.hunks().stream()
                .flatMap(h -> h.lines().stream())
                .filter(l -> l.type() == LineType.ADD && l.content().trim().startsWith("import "))
                .collect(Collectors.toMap(HunkLine::newLineNumber, HunkLine::content, (a, b) -> a));

        if (addedImportLines.isEmpty()) {
            return List.of();
        }

        String fullSource = ctx.contentProvider().fetch(file, ctx.mr());
        if (fullSource == null) {
            return List.of();
        }

        // 简单名引用计数（文本统计足够准：类名引用必然以该名出现）
        // 同名冲突的 import 简单名集合 → 保守跳过
        Map<String, Long> simpleNameCount = cu.getImports().stream()
                .map(this::simpleName)
                .collect(Collectors.groupingBy(n -> n, Collectors.counting()));
        List<String> ambiguous = simpleNameCount.entrySet().stream()
                .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();

        List<ReviewFinding> findings = new java.util.ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            int line = imp.getRange().map(r -> r.begin.line).orElse(-1);
            if (!addedImportLines.containsKey(line)) {
                continue;   // 历史 import，本次未改
            }
            String simple = simpleName(imp);
            if (ambiguous.contains(simple)) {
                continue;   // 同名 import 冲突，无法判定
            }
            if (countOccurrences(fullSource, simple) <= 1) {
                findings.add(hit(file, line, "新增 import '" + imp.getNameAsString() + "' 未被使用，建议移除", null));
            }
        }
        return findings;
    }

    private String simpleName(ImportDeclaration imp) {
        String name = imp.getNameAsString();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private int countOccurrences(String source, String token) {
        int count = 0, idx = 0;
        while ((idx = source.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }
}
