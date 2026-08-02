package io.github.lalala1314521.codereviewagent.api.dto;

/**
 * 管理台可见的 Agent 元数据。刻意不包含 apiKey/baseUrl，避免泄露敏感配置。
 */
public record AgentInfo(
        String provider,
        String displayName,
        String model,
        boolean active,
        boolean available,
        boolean defaultAgent
) {
}
