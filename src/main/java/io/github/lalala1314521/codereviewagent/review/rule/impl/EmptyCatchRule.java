package io.github.lalala1314521.codereviewagent.review.rule.impl;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.model.LineType;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.review.rule.AbstractRule;
import io.github.lalala1314521.codereviewagent.review.rule.RuleContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 空 catch 块检测（ERROR / java）。
 *
 * <p>两种形态：单行 {@code catch (Exception e) {}}；或 catch 行后第一个非空行直接是 {@code }}。
 * 异常被吞后系统"带病运行"，问题到下游才爆发，属可判死的确定性问题，故定 ERROR。
 */
@Component
public class EmptyCatchRule extends AbstractRule {

    private static final Pattern CATCH_LINE = Pattern.compile("catch\\s*\\(");
    private static final Pattern SINGLE_LINE_EMPTY = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*}");
    private static final Pattern CLOSE_BRACE = Pattern.compile("^}\\s*(catch|finally|\\{|})?.*$");

    @Override
    public String ruleId() {
        return "empty_catch";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String applicableLanguage() {
        return "java";
    }

    @Override
    public List<ReviewFinding> apply(DiffFile file, RuleContext ctx) {
        List<ReviewFinding> hits = new ArrayList<>();
        List<HunkLine> lines = allLines(file);   // 需要上下文行判断"块内为空"

        for (int i = 0; i < lines.size(); i++) {
            HunkLine line = lines.get(i);
            // 只看新增行（问题是本次 MR 引入的）
            if (line.type() != LineType.ADD || !CATCH_LINE.matcher(line.content()).find()) {
                continue;
            }
            String content = line.content();
            // 形态 1：单行空 catch
            if (SINGLE_LINE_EMPTY.matcher(content).find()) {
                hits.add(hit(file, line.newLineNumber(),
                        "catch 块为空，异常被吞掉，无日志无重抛",
                        "至少记录日志 log.error(\"...\", e)，或包装后重抛；确实可忽略时加注释说明原因"));
                continue;
            }
            // 形态 2：catch 行以 { 结尾，向下找第一个非空行，若是 } 则块为空
            if (content.trim().endsWith("{")) {
                for (int j = i + 1; j < lines.size(); j++) {
                    String next = lines.get(j).content().trim();
                    if (next.isEmpty()) {
                        continue;
                    }
                    // 跳过注释行（// ...），纯注释的 catch 视为空（异常仍被吞）
                    if (next.startsWith("//")) {
                        continue;
                    }
                    if (CLOSE_BRACE.matcher(next).matches()) {
                        hits.add(hit(file, line.newLineNumber(),
                                "catch 块为空，异常被吞掉，无日志无重抛",
                                "至少记录日志 log.error(\"...\", e)，或包装后重抛；确实可忽略时加注释说明原因"));
                    }
                    break;
                }
            }
        }
        return hits;
    }
}
