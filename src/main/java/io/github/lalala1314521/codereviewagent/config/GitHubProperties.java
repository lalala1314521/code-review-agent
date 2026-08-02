package io.github.lalala1314521.codereviewagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub 相关配置（application.yml 的 github.* 节点）。
 *
 * <p>apiUrl：官方 https://api.github.com（企业版可改）；
 * token：Personal Access Token（repo 权限），拉 diff/评论回写用；
 * webhookSecret：仓库 webhook 的 Secret，HMAC-SHA256 验签用。
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(
        String apiUrl,
        String token,
        String webhookSecret
) {}
