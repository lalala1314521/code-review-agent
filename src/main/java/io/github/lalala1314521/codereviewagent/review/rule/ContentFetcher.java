package io.github.lalala1314521.codereviewagent.review.rule;

import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.HunkLine;
import io.github.lalala1314521.codereviewagent.model.LineType;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.platform.PlatformRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 完整文件内容提供器：AST 规则的数据源。
 *
 * <p>diff 只有变更行，语法树分析需要全量源码：
 * <ul>
 *   <li>新增文件（isAdded）：diff 即全量，由新增行直接拼接，零网络请求</li>
 *   <li>变更文件：走平台 raw 文件 API（GitLab/GitHub）拉取</li>
 *   <li>失败/非 Java：返回 null，AST 规则降级跳过，正则规则不受影响</li>
 * </ul>
 */
@Component
public class ContentFetcher implements RuleContext.FullContentProvider {

    private static final Logger log = LoggerFactory.getLogger(ContentFetcher.class);
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final PlatformRouter platformRouter;

    public ContentFetcher(PlatformRouter platformRouter) {
        this.platformRouter = platformRouter;
    }

    @Override
    public String fetch(DiffFile file, UnifiedMergeRequest mr) {
        String path = file.newPath() != null ? file.newPath() : file.oldPath();
        if (path == null) {
            return null;
        }
        if (cache.containsKey(path)) {
            return cache.get(path);
        }
        String content = file.isAdded() ? fromAddedLines(file) : platformRouter.fetchRawFile(mr, file, path);
        if (content == null) {
            log.debug("full content unavailable for {}", path);
        }
        cache.put(path, content);
        return content;
    }

    private String fromAddedLines(DiffFile file) {
        StringBuilder sb = new StringBuilder();
        for (HunkLine line : file.hunks().stream().flatMap(h -> h.lines().stream()).toList()) {
            if (line.type() == LineType.ADD) {
                sb.append(line.content()).append('\n');
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
