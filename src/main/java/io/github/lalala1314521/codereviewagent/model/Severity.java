package io.github.lalala1314521.codereviewagent.model;

/**
 * 严重度分级。
 *
 * <p>ERROR：阻塞级，安全漏洞/数据风险，判 BLOCK；
 * WARNING：需修复，工程问题，计入 needs_fix_count；
 * INFO：建议，不影响通过率。
 */
public enum Severity {
    ERROR,   // 阻塞级：SQL 注入、硬编码密钥、明确空指针、catch 完全为空
    WARNING, // 需修复：新增业务类无测试、明确批量操作无事务
    INFO     // 建议：TODO/FIXME、魔法数字
}
