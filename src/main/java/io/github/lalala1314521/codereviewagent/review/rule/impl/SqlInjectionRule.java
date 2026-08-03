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
import java.util.regex.Pattern;

/**
 * SQL 注入检测（ERROR / 全语言）。
 *
 * <p>三种模式（只看新增行）：
 * <ul>
 *   <li>SQL 关键字字符串字面量后跟 {@code +} 拼接（Java/Go/C# 风格）</li>
 *   <li>执行方法实参含 {@code +} 拼接（jdbcTemplate.query("..." + cond)）</li>
 *   <li>Python f-string 模板内嵌变量：{@code f"SELECT ... {var}"}（v2026.08 多语言支持）</li>
 * </ul>
 *
 * <p>误报控制：MyBatis 的 #{}/${} 与 JDBC 的 ? 参数化写法不算拼接；每文件最多报 3 条。
 */
@Component
public class SqlInjectionRule extends AbstractRule {

    /** SQL 关键字字符串 + 变量拼接（Java/Go/C# 风格 "..." + var） */
    private static final Pattern SQL_CONCAT = Pattern.compile(
            "\"[^\"]*(?i:select|insert\\s+into|update|delete\\s+from)[^\"]*\"\\s*\\+");
    /** Python f-string 模板内嵌变量（f"SELECT ... {var}"） */
    private static final Pattern FSTRING_SQL = Pattern.compile(
            "f\"[^\"]*(?i:select|insert\\s+into|update|delete\\s+from)[^\"]*\\{[^}]+\\}");
    /** TS/JS 反引号模板字符串内嵌变量（`SELECT ... ${var}`） */
    private static final Pattern TEMPLATE_SQL = Pattern.compile(
            "`[^`]*(?i:select|insert\\s+into|update|delete\\s+from)[^`]*\\$\\{[^}]+\\}");
    /** 执行方法实参含拼接 */
    private static final Pattern EXECUTE_CONCAT = Pattern.compile(
            "\\.(executeQuery|executeUpdate|execute|query|queryForObject|queryForList|createQuery|createNativeQuery|prepareStatement)\\s*\\([^)]*\\+");
    /** 参数化占位符（安全写法，排除） */
    private static final Pattern SAFE_PLACEHOLDER = Pattern.compile("#\\{|\\?|%s|%\\(");

    private static final int MAX_HITS_PER_FILE = 3;

    @Override
    public String ruleId() {
        return "sql_injection";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public String applicableLanguage() {
        return null;   // 多语言通用模式（Java/Go/TS/Python 拼接风格）
    }

    @Override
    public List<ReviewFinding> apply(DiffFile file, RuleContext ctx) {
        List<ReviewFinding> hits = new ArrayList<>();
        for (HunkLine line : addedLines(file)) {
            if (hits.size() >= MAX_HITS_PER_FILE) {
                break;
            }
            if (isCommentOrBlank(line)) {
                continue;
            }
            String content = line.content();
            boolean matched = SQL_CONCAT.matcher(content).find()
                    || FSTRING_SQL.matcher(content).find()
                    || TEMPLATE_SQL.matcher(content).find()
                    || (EXECUTE_CONCAT.matcher(content).find() && !SAFE_PLACEHOLDER.matcher(content).find());
            if (matched) {
                hits.add(hit(file, line.newLineNumber(),
                        "字符串拼接 SQL 存在注入风险，攻击者可构造输入篡改 SQL 语义",
                        "使用参数化绑定（PreparedStatement 的 ? / MyBatis 的 #{} / 驱动占位符），禁止拼接 SQL"));
            }
        }
        return hits;
    }
}
