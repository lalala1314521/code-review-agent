package io.github.lalala1314521.codereviewagent.review.rule.ast;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.review.rule.AbstractRule;
import io.github.lalala1314521.codereviewagent.review.rule.RuleContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AST 规则基类：完整源码解析 + diff 关联过滤。
 *
 * <p>正则规则只看 diff 行；AST 规则需要全量源码（JavaParser 解析），
 * 内容来自 {@link RuleContext#contentProvider()}（新增文件拼 diff，变更文件走平台 raw API）。
 * 解析失败/无内容返回 null，规则降级跳过。
 */
public abstract class AbstractAstRule extends AbstractRule {

    private final Map<String, CompilationUnit> parseCache = new ConcurrentHashMap<>();

    /** 解析文件为 AST；无内容/解析失败返回 null。 */
    protected CompilationUnit parse(DiffFile file, RuleContext ctx) {
        String content = ctx.contentProvider().fetch(file, ctx.mr());
        if (content == null || content.isBlank()) {
            return null;
        }
        String key = (file.newPath() != null ? file.newPath() : file.oldPath()) + "#" + ruleId();
        return parseCache.computeIfAbsent(key, k -> {
            try {
                return StaticJavaParser.parse(content);
            } catch (Exception e) {
                return null;
            }
        });
    }

    /** 方法体范围内是否有 diff 新增行（只报本次变更触碰的方法）。 */
    protected boolean touchedByDiff(MethodDeclaration method, DiffFile file) {
        int begin = method.getRange().map(r -> r.begin.line).orElse(-1);
        int end = method.getRange().map(r -> r.end.line).orElse(-1);
        for (HunkLine line : file.addedLines()) {
            if (line.newLineNumber() >= begin && line.newLineNumber() <= end) {
                return true;
            }
        }
        return false;
    }
}
