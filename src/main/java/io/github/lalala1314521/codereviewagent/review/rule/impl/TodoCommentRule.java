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
 * TODO/FIXME 遗留标记检测（INFO / 全语言）。
 *
 * <p>只报每条文件第一处（INFO 级不刷屏）；TODO 只影响可维护性，不影响合并结论。
 */
@Component
public class TodoCommentRule extends AbstractRule {

    private static final Pattern TODO_PATTERN = Pattern.compile("\\b(TODO|FIXME)\\b");

    @Override
    public String ruleId() {
        return "todo_comment";
    }

    @Override
    public Severity severity() {
        return Severity.INFO;
    }

    @Override
    public String applicableLanguage() {
        return null;
    }

    @Override
    public List<ReviewFinding> apply(DiffFile file, RuleContext ctx) {
        for (HunkLine line : addedLines(file)) {
            if (TODO_PATTERN.matcher(line.content()).find()) {
                return List.of(hit(file, line.newLineNumber(),
                        "遗留 TODO/FIXME 待办标记，合入前建议处理或登记跟踪",
                        "完成待办事项，或转为 Issue/任务跟踪后在注释中引用单号"));
            }
        }
        return List.of();
    }
}
