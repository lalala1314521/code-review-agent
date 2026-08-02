package io.github.lalala1314521.codereviewagent.model;

import java.util.List;

/**
 * Diff 文件切片（对齐方案设计 10.2）。
 *
 * <p>一个 MR 的 diff 由 N 个 DiffFile 组成；规则引擎以它为输入单位逐文件扫描。
 *
 * @param oldPath   旧路径（重命名时与 newPath 不同）
 * @param newPath   新路径
 * @param isDeleted 是否删除文件
 * @param isRenamed 是否重命名
 * @param isAdded   是否新增文件
 * @param language  由后缀推断：java / go / ts / py / js / sql / yaml；unknown 表示未识别
 * @param hunks     变更块列表
 */
public record DiffFile(
        String oldPath,
        String newPath,
        boolean isDeleted,
        boolean isRenamed,
        boolean isAdded,
        String language,
        List<DiffHunk> hunks
) {
    /**
     * 该文件所有新增行（规则扫描的主要输入）。
     */
    public List<HunkLine> addedLines() {
        return hunks.stream()
                .flatMap(h -> h.lines().stream())
                .filter(l -> l.type() == LineType.ADD)
                .toList();
    }
}
