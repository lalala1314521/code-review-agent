package io.github.lalala1314521.codereviewagent.api;

import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewRecordMapper;
import io.github.lalala1314521.codereviewagent.platform.PlatformRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库/分支上下文 API（前端"上下文切换器"数据源）。
 *
 * <p>GET /api/v1/contexts?refresh=false → 仓库树：
 * <pre>
 * [
 *   { "repoPath": "demo/shop-backend", "platform": "GITLAB",
 *     "branches": [ {"name": "feat/user-service", "mrCount": 5}, ... ] },
 *   { "repoPath": "local-project", "platform": "LOCAL", ... }
 * ]
 * </pre>
 * platform 分组即"远程 / 本地"：GITLAB/GITHUB 为远程，LOCAL 为本地文件审查。
 *
 * <p>数据两层：存量层（默认）从 review_record 聚合，零外部依赖；勾取层（refresh=true）
 * 对每个 GITLAB 仓调 API 拉全量分支合并，<b>单仓失败分仓降级</b>——保留存量分支，
 * 其他仓不受影响，接口永不因勾取失败而 500。
 */
@RestController
@RequestMapping("/api/v1/contexts")
public class ContextController {

    private static final Logger log = LoggerFactory.getLogger(ContextController.class);

    private final ReviewRecordMapper recordMapper;
    private final PlatformRouter platformRouter;

    public ContextController(ReviewRecordMapper recordMapper, PlatformRouter platformRouter) {
        this.recordMapper = recordMapper;
        this.platformRouter = platformRouter;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> tree(@RequestParam(defaultValue = "false") boolean refresh) {
        List<Map<String, Object>> rows = recordMapper.selectRepoBranchTree();

        // 按 platform+repoPath 聚合成树（同路径在 GitLab/GitHub 可能是两个不同仓库，不能合并）
        Map<String, Map<String, Object>> repoMap = new LinkedHashMap<>();
        // mapKey → projectId（同仓取最大 project_id，用于勾分支）
        Map<String, Long> projectIds = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String platform = String.valueOf(row.get("platform"));
            String repoPath = String.valueOf(row.get("repoPath"));
            String mapKey = platform + "|" + repoPath;
            Map<String, Object> repo = repoMap.computeIfAbsent(mapKey, k -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("repoPath", repoPath);
                r.put("platform", platform);
                r.put("branches", new ArrayList<Map<String, Object>>());
                return r;
            });
            Object pid = row.get("projectId");
            if (pid != null) {
                projectIds.put(mapKey, ((Number) pid).longValue());
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> branches = (List<Map<String, Object>>) repo.get("branches");
            Map<String, Object> branch = new LinkedHashMap<>();
            branch.put("name", row.get("branch") == null ? "(未知分支)" : String.valueOf(row.get("branch")));
            branch.put("mrCount", row.get("mrCount"));
            branches.add(branch);
        }

        // refresh=true 时从平台 API 拉远程仓库全量分支（分仓降级）
        if (refresh) {
            enrichFromPlatforms(repoMap, projectIds);
        }
        return ApiResponse.ok(new ArrayList<>(repoMap.values()));
    }

    /**
     * 对每个远程仓库（GITLAB/GITHUB）调对应平台 API 拉分支合并进树。
     * 单仓失败仅降级该仓（保留存量），不影响其他仓与整体响应。
     */
    private void enrichFromPlatforms(Map<String, Map<String, Object>> repoMap, Map<String, Long> projectIds) {
        for (Map.Entry<String, Map<String, Object>> entry : repoMap.entrySet()) {
            Map<String, Object> repo = entry.getValue();
            String platform = String.valueOf(repo.get("platform"));
            if (!"GITLAB".equals(platform) && !"GITHUB".equals(platform)) {
                continue;   // 只勾取远程仓库（LOCAL 无远端）
            }
            try {
                List<String> remoteBranches = platformRouter.getBranches(
                        platform, String.valueOf(repo.get("repoPath")), projectIds.get(entry.getKey()));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> branches = (List<Map<String, Object>>) repo.get("branches");
                for (String name : remoteBranches) {
                    boolean exists = branches.stream().anyMatch(b -> name.equals(String.valueOf(b.get("name"))));
                    if (!exists) {
                        Map<String, Object> branch = new LinkedHashMap<>();
                        branch.put("name", name);
                        branch.put("mrCount", 0);   // 远端有、本地暂无审查记录
                        branches.add(branch);
                    }
                }
                repo.put("remoteFetched", true);
                log.info("context branches enriched platform={} repo={} remoteBranches={}",
                        platform, repo.get("repoPath"), remoteBranches.size());
            } catch (Exception e) {
                // 单仓降级：保留存量分支，标记未勾取成功
                repo.put("remoteFetched", false);
                log.warn("context enrich failed platform={} repo={}: {}", platform, repo.get("repoPath"), e.getMessage());
            }
        }
    }
}
