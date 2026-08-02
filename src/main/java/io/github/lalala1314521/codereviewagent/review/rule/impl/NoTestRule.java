package io.github.lalala1314521.codereviewagent.review.rule.impl;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.review.rule.AbstractRule;
import io.github.lalala1314521.codereviewagent.review.rule.RuleContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 缺少测试检测（WARNING / java，跨文件规则）。
 *
 * <p>判定：<b>新增的业务类</b>（isAdded 且命名命中 Service/Controller/Manager 等后缀）
 * 且整个 MR 的 diff 中<b>没有任何测试文件</b>（路径含 Test/Spec 或位于 test 目录）。
 *
 * <p>这是规则引擎"跨文件视角"的典型场景——单看一个文件判断不了"有没有配套测试"，
 * 必须拿到全量 diff 文件列表（{@code ctx.allFiles()}）。
 *
 * <p>注意限定 isAdded：修改存量类不强制补测试（历史债不应由每个小改动偿还），
 * 新增类是"第一次写测试成本最低"的时机。
 */
@Component
public class NoTestRule extends AbstractRule {

    /** 业务类命名后缀（这些类承载业务逻辑，应有测试） */
    private static final Pattern BIZ_CLASS_NAME = Pattern.compile(
            "(Service|Controller|Manager|Repository|Dao|Handler|Component|Facade|Helper)\\.(java|kt)$");
    /** 测试文件特征 */
    private static final Pattern TEST_FILE = Pattern.compile(
            "(?i)(/test/|Test\\.(java|kt|go|py|ts|js)$|Tests\\.(java|kt|go|py|ts|js)$|Spec\\.(go|py|ts|js)$)");
    private static final Pattern CLASS_DECL = Pattern.compile("\\b(class|interface|enum)\\s+\\w+");

    @Override
    public String ruleId() {
        return "no_test";
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
        String path = file.newPath();
        if (!file.isAdded() || path == null || !BIZ_CLASS_NAME.matcher(path).find()) {
            return List.of();
        }

        // 全 MR 范围找测试文件
        boolean hasTest = ctx.allFiles().stream()
                .map(f -> f.newPath() != null ? f.newPath() : f.oldPath())
                .filter(java.util.Objects::nonNull)
                .anyMatch(p -> TEST_FILE.matcher(p).find());
        if (hasTest) {
            return List.of();
        }

        // 报在类声明行（找不到则文件级）
        Integer classLine = addedLines(file).stream()
                .filter(l -> CLASS_DECL.matcher(l.content()).find())
                .map(HunkLine::newLineNumber)
                .findFirst()
                .orElse(null);
        return List.of(hit(file, classLine,
                "新增业务类但 diff 中无对应测试文件，核心业务逻辑缺少测试保护",
                "补充单元测试（" + simpleName(path) + "Test），覆盖正常路径与边界条件"));
    }

    private String simpleName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
    }
}
