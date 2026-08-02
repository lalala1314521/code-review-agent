package io.github.lalala1314521.codereviewagent.platform;

import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.platform.github.GitHubClient;
import io.github.lalala1314521.codereviewagent.platform.gitlab.GitLabClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台路由器：按 {@code mr.platform()} 把 diff 拉取 / 评论回写 / 分支勾取分发到对应平台 Client。
 *
 * <p>不抽统一 PlatformClient 接口：两平台能力形态差异大（GitLab 数字 projectId + 拼多文件 diff；
 * GitHub owner/repo 路径 + 单请求整段 diff），强抽象只会变成"漏抽象"再 if-else 平台。
 * 差异留在各自 Client，调用方只面对 {@code mr.platform()} 一个分发点，新增平台只加一个 case。
 *
 * <p>LOCAL 平台（本地文件审查）：diff 已随请求填充、无评论可回写，
 * 调用方应走 {@code ReviewEngine#reviewWithDiff} 链路而非本路由器。
 */
@Component
public class PlatformRouter {

    private static final Logger log = LoggerFactory.getLogger(PlatformRouter.class);

    private final GitLabClient gitLabClient;
    private final GitHubClient gitHubClient;

    public PlatformRouter(GitLabClient gitLabClient, GitHubClient gitHubClient) {
        this.gitLabClient = gitLabClient;
        this.gitHubClient = gitHubClient;
    }

    /**
     * 拉取 MR/PR 的 unified diff。
     */
    public String getDiff(UnifiedMergeRequest mr) {
        return switch (mr.platform()) {
            case "GITLAB" -> gitLabClient.getMrDiff(mr.projectId(), mr.mrIid());
            case GitHubClient.PLATFORM -> gitHubClient.getPrDiff(mr.repoPath(), mr.mrIid());
            default -> throw new IllegalArgumentException("unsupported platform for diff: " + mr.platform());
        };
    }

    /**
     * 回写 MR/PR 评论。
     */
    public void postComment(UnifiedMergeRequest mr, String body) {
        switch (mr.platform()) {
            case "GITLAB" -> gitLabClient.postMrNote(mr.projectId(), mr.mrIid(), body);
            case GitHubClient.PLATFORM -> gitHubClient.postPrComment(mr.repoPath(), mr.mrIid(), body);
            default -> log.warn("postComment unsupported platform={}, skip", mr.platform());
        }
    }

    /**
     * 勾取仓库分支列表（上下文切换器用）；不支持的平台返回空列表。
     */
    public List<String> getBranches(String platform, String repoPath, Long projectId) {
        return switch (platform) {
            case "GITLAB" -> gitLabClient.getBranches(projectId);
            case GitHubClient.PLATFORM -> gitHubClient.getBranches(repoPath);
            default -> List.of();
        };
    }
}
