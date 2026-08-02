package io.github.lalala1314521.codereviewagent.model;

/**
 * diff 行（对齐方案设计 10.2）。
 *
 * @param type          ADD / DEL / CONTEXT
 * @param oldLineNumber 旧文件行号；-1 表示新增行
 * @param newLineNumber 新文件行号；-1 表示删除行
 * @param content       行内容（不含 +/-/空格前缀）
 */
public record HunkLine(
        LineType type,
        int oldLineNumber,
        int newLineNumber,
        String content
) {}
