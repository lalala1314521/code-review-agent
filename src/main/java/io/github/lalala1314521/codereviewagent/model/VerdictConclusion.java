package io.github.lalala1314521.codereviewagent.model;

/**
 * 裁决结论。
 *
 * <p>APPROVE：建议合并；NEEDS_FIX：需修复；BLOCK：阻塞。
 */
public enum VerdictConclusion {
    APPROVE,      // 建议合并
    NEEDS_FIX,    // 需修复
    BLOCK         // 阻塞（含 error 级别问题）
}
