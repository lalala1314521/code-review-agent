package io.github.lalala1314521.codereviewagent.api.dto;

/** 审查上下文聊天响应。 */
public record ReviewChatResponse(
        String answer,
        String provider,
        String model,
        String agentName
) {
}
