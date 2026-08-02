package io.github.lalala1314521.codereviewagent.review.rule;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.model.LineType;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;

/**
 * 规则实现基类：收敛 finding 构造与行判断的样板代码。
 */
public abstract class AbstractRule implements ReviewRule {

    /**
     * 构造一条规则命中：source=RULE，confidence=1.0（规则命中即确定，不像 LLM 有概率）。
     */
    protected ReviewFinding hit(DiffFile file, Integer lineNumber, String message, String suggestion) {
        String path = file.newPath() != null ? file.newPath() : file.oldPath();
        return new ReviewFinding(path, lineNumber, severity(), ruleId(), message, suggestion, "RULE", 1.0);
    }

    /**
     * 该行是否为纯注释/空行（Java 风格）——magic_number 等规则需要排除。
     */
    protected boolean isCommentOrBlank(HunkLine line) {
        String t = line.content().trim();
        return t.isEmpty()
                || t.startsWith("//")
                || t.startsWith("/*")
                || t.startsWith("*/")
                || t.startsWith("*");
    }

    /**
     * 该行是否为结构性行（import/package/注解）——不参与代码模式检测。
     */
    protected boolean isStructural(HunkLine line) {
        String t = line.content().trim();
        return t.startsWith("import ")
                || t.startsWith("package ")
                || t.startsWith("@");
    }

    /**
     * 文件的全部行（ADD + DEL + CONTEXT 按顺序）——跨行模式（如空 catch）需要看前后文。
     */
    protected java.util.List<HunkLine> allLines(DiffFile file) {
        return file.hunks().stream()
                .flatMap(h -> h.lines().stream())
                .toList();
    }

    /**
     * 文件的新增行。
     */
    protected java.util.List<HunkLine> addedLines(DiffFile file) {
        return file.hunks().stream()
                .flatMap(h -> h.lines().stream())
                .filter(l -> l.type() == LineType.ADD)
                .toList();
    }
}
