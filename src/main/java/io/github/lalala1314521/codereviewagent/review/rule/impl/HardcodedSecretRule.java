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
 * 硬编码密钥检测（ERROR / 全语言）。
 *
 * <p>模式：敏感词赋值字面量，覆盖 Java 与 YAML/Properties 两种风格：
 * <ul>
 *   <li>{@code apiKey = "abcd1234..."} / {@code password = "..."}</li>
 *   <li>{@code api_key: abcd1234...} / {@code secret: ...}</li>
 * </ul>
 *
 * <p>误报控制：占位符（${...} / your-xxx / example / changeme）与过短值（<6）不报。
 */
@Component
public class HardcodedSecretRule extends AbstractRule {

    private static final Pattern SECRET_ASSIGN = Pattern.compile(
            "(?i)(api[_-]?key|secret|password|passwd|token|access[_-]?key|private[_-]?key)"
                    + "\\s*[:=]\\s*\"?([A-Za-z0-9_/+\\-.]{6,})\"?");
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)(\\$\\{|your[-_]|example|changeme|xxx+|placeholder|<.*>|\\*+)");

    private static final int MAX_HITS_PER_FILE = 3;

    @Override
    public String ruleId() {
        return "hardcoded_secret";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
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
            var m = SECRET_ASSIGN.matcher(line.content());
            if (!m.find()) {
                continue;
            }
            String value = m.group(2);
            if (PLACEHOLDER.matcher(value).find()) {
                continue;   // 占位符，非真实密钥
            }
            hits.add(hit(file, line.newLineNumber(),
                    "疑似硬编码密钥（" + m.group(1) + "），敏感信息不应提交到代码库",
                    "改用环境变量 / 配置中心 / 密钥管理服务（KMS），并立即轮换该密钥"));
        }
        return hits;
    }
}
