package io.github.lalala1314521.codereviewagent.review.rule.impl;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.DiffHunk;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.model.LineType;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.review.rule.AbstractRule;
import io.github.lalala1314521.codereviewagent.review.rule.RuleContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 超大函数检测（WARNING / java，近似判定）。
 *
 * <p>diff 里看不到完整函数边界，用"单个 hunk 内<b>连续新增行数</b>"近似：
 * 连续新增超过 maxLines（默认 80，rule.params_json 可配）说明开发者在往
 * 一个方法/类里塞大段新逻辑，疑似超大函数。
 *
 * <p>参数化示例：rule.params_json = {"maxLines": 80} —— 阈值在管理台改 DB 即生效，
 * 不用改代码（对齐方案设计 14.3）。
 */
@Component
public class OversizedFunctionRule extends AbstractRule {

    @Override
    public String ruleId() {
        return "oversized_function";
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
        int maxLines = ctx.paramAsInt("maxLines", 80);
        List<ReviewFinding> hits = new ArrayList<>();

        for (DiffHunk hunk : file.hunks()) {
            int consecutive = 0;
            int blockStart = -1;
            for (HunkLine line : hunk.lines()) {
                if (line.type() == LineType.ADD) {
                    if (consecutive == 0) {
                        blockStart = line.newLineNumber();
                    }
                    consecutive++;
                } else {
                    consecutive = 0;   // 被上下文/删除行打断，重新计
                }
                if (consecutive > maxLines) {
                    hits.add(hit(file, blockStart,
                            "单次新增连续 " + consecutive + " 行（阈值 " + maxLines + "），疑似超大函数，建议拆分",
                            "按职责抽取私有方法，控制单方法行数在阈值内（可用 Extract Method 重构）"));
                    break;   // 每 hunk 只报一次
                }
            }
        }
        return hits;
    }
}
