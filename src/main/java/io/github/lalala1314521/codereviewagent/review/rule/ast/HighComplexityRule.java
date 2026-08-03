package io.github.lalala1314521.codereviewagent.review.rule.ast;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.review.rule.RuleContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 方法圈复杂度过高（WARNING / java，阈值参数 maxComplexity 默认 15）。
 *
 * <p>AST 分析：1 + if/for/while/do/case/catch/&&/||/?: 计数。
 * 只报"本次 diff 变更触碰的方法"——历史遗留的高复杂度方法不属于本次变更责任。
 */
@Component
public class HighComplexityRule extends AbstractAstRule {

    @Override
    public String ruleId() {
        return "high_complexity";
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
        int threshold = ctx.paramAsInt("maxComplexity", 15);
        CompilationUnit cu = parse(file, ctx);
        if (cu == null) {
            return List.of();
        }
        List<ReviewFinding> findings = new java.util.ArrayList<>();
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (!touchedByDiff(method, file)) {
                continue;
            }
            int complexity = measure(method);
            if (complexity > threshold) {
                int line = method.getRange().map(r -> r.begin.line).orElse(-1);
                String name = method.getNameAsString();
                findings.add(hit(file, line,
                        "方法 '" + name + "' 圈复杂度 " + complexity + "，超过阈值 " + threshold + "，建议拆分", null));
            }
        }
        return findings;
    }

    private int measure(MethodDeclaration method) {
        AtomicInteger count = new AtomicInteger(1);
        method.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(IfStmt n, Void arg) { count.incrementAndGet(); super.visit(n, arg); }
            @Override
            public void visit(ForStmt n, Void arg) { count.incrementAndGet(); super.visit(n, arg); }
            @Override
            public void visit(ForEachStmt n, Void arg) { count.incrementAndGet(); super.visit(n, arg); }
            @Override
            public void visit(WhileStmt n, Void arg) { count.incrementAndGet(); super.visit(n, arg); }
            @Override
            public void visit(DoStmt n, Void arg) { count.incrementAndGet(); super.visit(n, arg); }
            @Override
            public void visit(CatchClause n, Void arg) { count.incrementAndGet(); super.visit(n, arg); }
            @Override
            public void visit(SwitchEntry n, Void arg) {
                if (n.getLabels() != null && !n.getLabels().isEmpty()) { count.incrementAndGet(); }
                super.visit(n, arg);
            }
            @Override
            public void visit(BinaryExpr n, Void arg) {
                if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) {
                    count.incrementAndGet();
                }
                super.visit(n, arg);
            }
            @Override
            public void visit(ConditionalExpr n, Void arg) { count.incrementAndGet(); super.visit(n, arg); }
        }, null);
        return count.get();
    }
}
