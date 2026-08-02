package io.github.lalala1314521.codereviewagent.review.rule.impl;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.review.rule.AbstractRule;
import io.github.lalala1314521.codereviewagent.review.rule.RuleContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 魔法数字检测（INFO / 全语言）。
 *
 * <p>新增行中出现未常量化的数字字面量（默认忽略 0/1/-1/2——它们通常语义自明）。
 * 排除注释行、import/package/注解等结构性行。
 *
 * <p>INFO 级、每文件最多 2 条：魔法数字是风格建议不是缺陷，不刷屏、不影响 Verdict。
 */
@Component
public class MagicNumberRule extends AbstractRule {

    private static final Pattern NUMBER_LITERAL = Pattern.compile("(?<![\\w$.-])-?\\d+(?![\\w$.])");
    private static final Set<String> IGNORED = Set.of("0", "1", "-1", "2");
    private static final int MAX_HITS_PER_FILE = 2;

    @Override
    public String ruleId() {
        return "magic_number";
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
        List<ReviewFinding> hits = new ArrayList<>();
        for (HunkLine line : addedLines(file)) {
            if (hits.size() >= MAX_HITS_PER_FILE) {
                break;
            }
            if (isCommentOrBlank(line) || isStructural(line)) {
                continue;
            }
            Matcher m = NUMBER_LITERAL.matcher(line.content());
            while (m.find()) {
                String num = m.group();
                if (!IGNORED.contains(num)) {
                    hits.add(hit(file, line.newLineNumber(),
                            "魔法数字 " + num + " 未常量化，含义不自明",
                            "提取为命名常量（如 private static final int MAX_RETRIES = " + num + "）"));
                    break;   // 每行只报一次
                }
            }
        }
        return hits;
    }
}
