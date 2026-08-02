package io.github.lalala1314521.codereviewagent.platform.github.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitHub pull_request webhook 事件负载。
 *
 * <p>只取归一到 {@code UnifiedMergeRequest} 需要的字段，其余 ignoreUnknown。
 *
 * @param action      opened / synchronize / reopened / closed ...
 * @param number      PR 编号（仓库内）
 * @param pullRequest PR 主体
 * @param repository  仓库
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestPayload(
        String action,
        Long number,
        @JsonProperty("pull_request") PullRequest pullRequest,
        Repository repository
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(
            Long id,
            Long number,
            String title,
            String body,
            @JsonProperty("html_url") String htmlUrl,
            User user,
            RefSide head,
            RefSide base
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefSide(
            String ref,
            String sha,
            Repository repo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(
            String login
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(
            Long id,
            @JsonProperty("full_name") String fullName
    ) {}
}
