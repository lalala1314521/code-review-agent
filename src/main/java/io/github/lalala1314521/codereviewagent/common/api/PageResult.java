package io.github.lalala1314521.codereviewagent.common.api;

import java.util.List;

/**
 * 分页结果（对齐方案设计 12.3 的 items 结构）。
 */
public record PageResult<T>(long page, long size, long total, List<T> items) {

    public static <T> PageResult<T> of(long page, long size, long total, List<T> items) {
        return new PageResult<>(page, size, total, items);
    }
}
