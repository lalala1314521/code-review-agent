package io.github.lalala1314521.codereviewagent.model;

import java.util.List;

/**
 * Diff 变更块（对齐方案设计 10.2）。
 *
 * <p>一个文件的一次连续变更片段（@@ ... @@ 之间的内容）。
 *
 * @param oldPath      旧路径
 * @param newPath      新路径
 * @param oldStartLine 旧文件起始行
 * @param oldEndLine   旧文件结束行
 * @param newStartLine 新文件起始行
 * @param newEndLine   新文件结束行
 * @param lines        行级内容（ADD / DEL / CONTEXT）
 */
public record DiffHunk(
        String oldPath,
        String newPath,
        int oldStartLine,
        int oldEndLine,
        int newStartLine,
        int newEndLine,
        List<HunkLine> lines
) {}
