package io.github.lalala1314521.codereviewagent.platform.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.platform.gitlab.payload.GitLabMergeRequestPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GitLab API v4 客户端。
 *
 * <p>提供两个能力：
 * <ul>
 *   <li>{@link #toUnifiedMergeRequest} —— 把 webhook payload 归一成 {@link UnifiedMergeRequest}</li>
 *   <li>{@link #getMrDiff} —— 拉 MR 的 unified diff（调 {@code /merge_requests/:iid/changes}）</li>
 *   <li>{@link #postMrNote} —— 回写 MR 评论（调 {@code /merge_requests/:iid/notes}）</li>
 * </ul>
 *
 * <p>认证走 {@code PRIVATE-TOKEN} header，在 {@link io.github.lalala1314521.codereviewagent.config.RestClientConfig}
 * 里统一注入。MVP 阶段不做重试/限流（V1 起用 Resilience4j）。
 *
 * @see <a href="https://docs.gitlab.com/ee/api/merge_requests.html">GitLab Merge Requests API</a>
 */
@Component
public class GitLabClient {

    private static final Logger log = LoggerFactory.getLogger(GitLabClient.class);

    private final RestClient gitLabRestClient;

    public GitLabClient(@Qualifier("gitLabRestClient") RestClient gitLabRestClient) {
        this.gitLabRestClient = gitLabRestClient;
    }

    /**
     * 把 GitLab webhook payload 归一成统一 MR 模型（diff 字段未填充，需单独调 {@link #getMrDiff}）。
     */
    public UnifiedMergeRequest toUnifiedMergeRequest(GitLabMergeRequestPayload payload) {
        var attrs = payload.objectAttributes();
        var project = payload.project();
        var user = payload.user();
        return new UnifiedMergeRequest(
                "GITLAB",
                project.id(),
                project.pathWithNamespace(),
                attrs.iid(),
                attrs.lastCommit() != null ? attrs.lastCommit().id() : null,
                attrs.sourceBranch(),
                attrs.targetBranch(),
                attrs.title(),
                attrs.description(),
                user != null ? user.name() : null,
                user != null ? user.username() : null,
                attrs.url(),
                null  // diff 稍后由 getMrDiff() 填充
        );
    }

    /**
     * 拉 MR 的 unified diff 文本。
     *
     * <p>调 {@code GET /api/v4/projects/:id/merge_requests/:iid/changes}，
     * 把所有文件的 diff 拼成一段文本，喂给 LLM。
     *
     * @param projectId GitLab project.id（数字）
     * @param mrIid     MR 内部编号
     * @return 拼好的 unified diff 文本；无变更返回空串
     */
    public String getMrDiff(Long projectId, Long mrIid) {
        MrChangesResponse response = gitLabRestClient.get()
                .uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}/changes",
                        projectId, mrIid)
                .retrieve()
                .body(MrChangesResponse.class);

        if (response == null || response.changes() == null || response.changes().isEmpty()) {
            log.warn("no changes fetched for project={} mr={}", projectId, mrIid);
            return "";
        }

        String diff = response.changes().stream()
                .map(this::formatDiff)
                .collect(Collectors.joining("\n\n"));
        log.info("diff fetched project={} mr={} files={} chars={}",
                projectId, mrIid, response.changes().size(), diff.length());
        return diff;
    }

    /**
     * 拉取项目分支列表（上下文切换器"远程分支勾取"用）。
     *
     * <p>调 {@code GET /api/v4/projects/:id/repository/branches?per_page=100}。
     * 失败（网络/权限/GitLab 不可用）抛异常，由调用方降级——本方法不静默吞错，
     * 因为"勾取失败"和"项目真的没有分支"必须可区分。
     *
     * @param projectId GitLab project.id
     * @return 分支名列表（最多 100 个）
     */
    public java.util.List<String> getBranches(Long projectId) {
        BranchDto[] branches = gitLabRestClient.get()
                .uri("/api/v4/projects/{projectId}/repository/branches?per_page=100", projectId)
                .retrieve()
                .body(BranchDto[].class);
        if (branches == null) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(branches)
                .map(BranchDto::name)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 分支 DTO（GitLab /repository/branches 响应） */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record BranchDto(String name) {}

    /**
     * 回写一条 MR 总结评论。
     *
     * <p>调 {@code POST /api/v4/projects/:id/merge_requests/:iid/notes}。
     * MVP 阶段只发一条总结评论；V1 起加行级评论（discussions + position）。
     *
     * @param projectId GitLab project.id
     * @param mrIid     MR 内部编号
     * @param body      评论内容（支持 Markdown）
     */
    public void postMrNote(Long projectId, Long mrIid, String body) {
        gitLabRestClient.post()
                .uri("/api/v4/projects/{projectId}/merge_requests/{mrIid}/notes",
                        projectId, mrIid)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", body))
                .retrieve()
                .toBodilessEntity();
        log.info("comment posted project={} mr={} chars={}", projectId, mrIid, body.length());
    }

    /**
     * 把单个文件的 diff 格式化成标准 unified diff 段落。
     *
     * <p>GitLab API 返回的 diff 字段是 {@code @@ -1,5 +1,7 @@...} 部分，
     * 这里补上 {@code diff --git / --- / +++} 头，让 LLM 看到完整格式。
     */
    private String formatDiff(Change c) {
        String path = c.newPath() != null ? c.newPath() : c.oldPath();
        return "diff --git a/" + path + " b/" + path + "\n"
                + "--- a/" + (c.oldPath() != null ? c.oldPath() : "/dev/null") + "\n"
                + "+++ b/" + (c.newPath() != null ? c.newPath() : "/dev/null") + "\n"
                + (c.diff() != null ? c.diff() : "");
    }

    // ===== GitLab API 响应 DTO（内部使用） =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MrChangesResponse(
            @JsonProperty("changes") List<Change> changes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Change(
            @JsonProperty("old_path") String oldPath,
            @JsonProperty("new_path") String newPath,
            @JsonProperty("diff") String diff,
            @JsonProperty("new_file") boolean newFile,
            @JsonProperty("deleted_file") boolean deletedFile,
            @JsonProperty("renamed_file") boolean renamedFile
    ) {}
}
