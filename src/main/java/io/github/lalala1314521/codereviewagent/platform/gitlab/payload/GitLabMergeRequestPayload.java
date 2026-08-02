package io.github.lalala1314521.codereviewagent.platform.gitlab.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GitLab Merge Request Webhook Payload。
 *
 * <p>对应 GitLab Repo → Settings → Webhooks 勾选 Merge request events 时推送的 JSON。
 * 字段用 @JsonProperty 映射 GitLab 的 snake_case 命名到 Java camelCase。
 *
 * <p>只保留 MVP 需要的字段；其他字段用 @JsonIgnoreProperties(ignoreUnknown = true) 忽略。
 *
 * @see <a href="https://docs.gitlab.com/ee/user/project/integrations/webhook_events.html">GitLab Webhook Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabMergeRequestPayload(
        @JsonProperty("object_kind") String objectKind,
        @JsonProperty("object_attributes") ObjectAttributes objectAttributes,
        @JsonProperty("project") Project project,
        @JsonProperty("user") User user
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectAttributes(
            @JsonProperty("action") String action,           // open / update / reopen / close / merge
            @JsonProperty("iid") Long iid,                    // MR 内部编号
            @JsonProperty("source_branch") String sourceBranch,
            @JsonProperty("target_branch") String targetBranch,
            @JsonProperty("last_commit") LastCommit lastCommit,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("state") String state,
            @JsonProperty("url") String url
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(
            @JsonProperty("id") Long id,                       // 数字 ID，调 API 用
            @JsonProperty("path_with_namespace") String pathWithNamespace
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(
            @JsonProperty("name") String name,
            @JsonProperty("username") String username
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LastCommit(
            @JsonProperty("id") String id                      // commit SHA
    ) {}
}
