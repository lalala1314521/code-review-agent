package io.github.lalala1314521.codereviewagent.review.rule;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;

import java.util.List;
import java.util.Map;

/**
 * 规则执行上下文。
 *
 * @param params         该规则的参数（rule.params_json 解析结果），如 {"maxLines": "80"}
 * @param allFiles       本次 MR 的全部 diff 文件（跨文件规则用，如 no_test）
 * @param mr             MR 元信息
 * @param contentProvider 完整文件内容提供器（AST 规则用；diff 只有变更行，分析语法树需要全量源码）
 */
public record RuleContext(
        Map<String, String> params,
        List<DiffFile> allFiles,
        UnifiedMergeRequest mr,
        FullContentProvider contentProvider
) {

    /** 完整文件内容提供器；获取失败返回 null，AST 规则降级跳过。 */
    public interface FullContentProvider {
        String fetch(DiffFile file, UnifiedMergeRequest mr);
    }

    /**
     * 读 int 参数，缺省/非法值时回退默认值。
     */
    public int paramAsInt(String key, int defaultValue) {
        String v = params != null ? params.get(key) : null;
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
