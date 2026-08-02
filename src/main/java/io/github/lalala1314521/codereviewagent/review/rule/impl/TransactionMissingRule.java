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
 * 事务边界缺失检测（WARNING / java）。
 *
 * <p>判定：新增行中出现 <b>≥2 次写操作调用</b>（save/insert/update/delete 族），
 * 且文件全文（含上下文行）<b>无 @Transactional</b>。
 * 多次写入无事务 = 部分提交风险（第二次失败时第一次已落库）。
 *
 * <p>局限：diff 里看不到方法注解是否本来就存在（如果类上已有 @Transactional
 * 且未出现在 diff 上下文中，会误报）。所以只定 WARNING 由人确认——
 * 这正是"规则圈定嫌疑、LLM/人做最终语义判断"的分工边界。
 */
@Component
public class TransactionMissingRule extends AbstractRule {

    private static final Pattern WRITE_CALL = Pattern.compile(
            "\\.(save|saveAll|saveAndFlush|insert|insertBatch|update|updateById|delete|deleteById|deleteBatch|merge)\\s*\\(");
    private static final Pattern TRANSACTIONAL = Pattern.compile("@Transactional");

    @Override
    public String ruleId() {
        return "transaction_missing";
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
        // 文件全文有 @Transactional 则放行
        boolean hasTransactional = allLines(file).stream()
                .anyMatch(l -> TRANSACTIONAL.matcher(l.content()).find());
        if (hasTransactional) {
            return List.of();
        }

        // 新增行中统计写操作调用
        List<HunkLine> writeLines = addedLines(file).stream()
                .filter(l -> WRITE_CALL.matcher(l.content()).find())
                .toList();
        if (writeLines.size() < 2) {
            return List.of();
        }

        return List.of(hit(file, writeLines.get(0).newLineNumber(),
                "检测到 " + writeLines.size() + " 次写操作调用但未见 @Transactional，存在部分提交风险",
                "在方法或类上添加 @Transactional(rollbackFor = Exception.class)，保证多次写入的原子性"));
    }
}
