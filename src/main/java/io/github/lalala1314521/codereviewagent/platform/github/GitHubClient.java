package io.github.lalala1314521.codereviewagent.platform.github;

import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.platform.github.payload.GitHubPullRequestPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * GitHub API 客户端。
 *
 * <p>与 {@code GitLabClient} 的差异（不强行统一接口）：
 * diff 一次请求拿整段文本（{@code Accept: application/vnd.github.v3.diff}）；
 * 评论走 issues API；定位用 owner/repo 路径（projectId 字段兼容存 repo.id）。
 */
@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
    public static final String PLATFORM = "GITHUB";

    private final RestClient gitHubRestClient;

    public GitHubClient(@Qualifier("gitHubRestClient") RestClient gitHubRestClient) {
        this.gitHubRestClient = gitHubRestClient;
    }

    /**
     * 把 pull_request webhook payload 归一成统一 MR 模型（diff 稍后由 {@link #getPrDiff} 填充）。
     */
    public UnifiedMergeRequest toUnifiedMergeRequest(GitHubPullRequestPayload payload) {
        GitHubPullRequestPayload.PullRequest pr = payload.pullRequest();
        return new UnifiedMergeRequest(
                PLATFORM,
                payload.repository() != null ? payload.repository().id() : null,
                payload.repository() != null ? payload.repository().fullName() : null,
                pr.number(),
                pr.head() != null ? pr.head().sha() : null,
                pr.head() != null ? pr.head().ref() : null,
                pr.base() != null ? pr.base().ref() : null,
                pr.title(),
                pr.body(),
                pr.user() != null ? pr.user().login() : null,
                pr.user() != null ? pr.user().login() : null,
                pr.htmlUrl(),
                null
        );
    }

    /**
     * 拉 PR 的 unified diff（GitHub 直接返回文本，无需拼接）。
     *
     * @param repoPath owner/repo（如 octocat/Hello-World）
     * @param prNumber PR 编号
     */
    public String getPrDiff(String repoPath, Long prNumber) {
        String[] parts = splitRepoPath(repoPath);
        String diff = gitHubRestClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{prNumber}", parts[0], parts[1], prNumber)
                .accept(MediaType.valueOf("application/vnd.github.v3.diff"))
                .retrieve()
                .body(String.class);
        log.info("pr diff fetched repo={} pr={} chars={}", repoPath, prNumber,
                diff == null ? 0 : diff.length());
        return diff == null ? "" : diff;
    }

    /**
     * 回写 PR 评论（issues comments API）。
     */
    public void postPrComment(String repoPath, Long prNumber, String body) {
        String[] parts = splitRepoPath(repoPath);
        gitHubRestClient.post()
                .uri("/repos/{owner}/{repo}/issues/{prNumber}/comments", parts[0], parts[1], prNumber)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", body))
                .retrieve()
                .toBodilessEntity();
        log.info("pr comment posted repo={} pr={} chars={}", repoPath, prNumber, body.length());
    }

    /**
     * 拉仓库分支列表（上下文切换器"远程分支勾取"用）。
     */
    public List<String> getBranches(String repoPath) {
        String[] parts = splitRepoPath(repoPath);
        BranchDto[] branches = gitHubRestClient.get()
                .uri("/repos/{owner}/{repo}/branches?per_page=100", parts[0], parts[1])
                .retrieve()
                .body(BranchDto[].class);
        if (branches == null) {
            return List.of();
        }
        return java.util.Arrays.stream(branches)
                .map(BranchDto::name)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * repoPath（owner/repo）拆成两段。
     *
     * <p><b>为什么必须拆</b>：Spring UriTemplate 会把路径变量里的 {@code /}
     * 编码成 {@code %2F}——"owner/repo" 作为单变量传进去，URL 就变成
     * {@code /repos/owner%2Frepo/...}，GitHub 一律 404（2026-07-26 真实踩坑：
     * 手工 curl 路径正确返回 200，后端经 UriTemplate 编码后 404）。
     * 拆成两个变量分别填充才能保留真实斜杠。
     */
    private String[] splitRepoPath(String repoPath) {
        if (repoPath == null || !repoPath.contains("/")) {
            throw new IllegalArgumentException("非法 repoPath（期望 owner/repo）: " + repoPath);
        }
        return repoPath.split("/", 2);
    }

    /** 分支 DTO（/repos/{}/branches 响应） */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record BranchDto(String name) {}
}
