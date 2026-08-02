package io.github.lalala1314521.codereviewagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitLab 相关配置。
 *
 * <p>对应 application.yml 里的 gitlab.* 节点。
 */
@ConfigurationProperties(prefix = "gitlab")
public record GitLabProperties(
        String baseUrl,
        String token,
        String webhookSecret
) {}
