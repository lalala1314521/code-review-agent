package io.github.lalala1314521.codereviewagent.review.rule;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;

import java.util.List;

/**
 * 审查规则接口（策略模式）。
 *
 * <p>与 LLM 的分工：规则只做"模式确定"检查（SQL 拼接、硬编码密钥、空 catch、TODO），
 * 命中即真理（confidence=1.0）；语义判断（空指针、逻辑漏洞）留给 LLM。
 * 注册与开关分离：@Component 注册即获得能力，是否启用由 rule 表控制，
 * 新增规则 = 加实现类 + DB 插一行配置。
 */
public interface ReviewRule {

    /**
     * 规则唯一标识，与 rule 表的 rule_id 对应（如 sql_injection）。
     */
    String ruleId();

    /**
     * 命中时的严重度。
     */
    Severity severity();

    /**
     * 适用语言（DiffFile.language）；null 表示全语言。
     */
    String applicableLanguage();

    /**
     * 对单个 diff 文件执行扫描。
     *
     * @param file 当前 diff 文件
     * @param ctx  规则参数 + 全量文件 + MR 上下文
     * @return 命中的发现（source=RULE，confidence=1.0）；未命中返回空列表
     */
    List<ReviewFinding> apply(DiffFile file, RuleContext ctx);
}
