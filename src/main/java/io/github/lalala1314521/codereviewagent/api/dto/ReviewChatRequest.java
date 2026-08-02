package io.github.lalala1314521.codereviewagent.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 审查上下文聊天请求。
 *
 * @param provider    可选 Provider；为空时使用当前活动 Agent
 * @param agentName   前端自定义 Agent 显示名，仅用于角色称呼
 * @param instruction 用户自定义 Agent 指令，长度受限且不能替代系统安全约束
 * @param message     当前用户问题
 * @param history     最近对话，最多 12 条
 */
public record ReviewChatRequest(
        String provider,
        @Size(max = 80, message = "agentName 最长 80 字符") String agentName,
        @Size(max = 1200, message = "instruction 最长 1200 字符") String instruction,
        @NotBlank(message = "message 不能为空")
        @Size(max = 2000, message = "message 最长 2000 字符") String message,
        @Valid @Size(max = 12, message = "history 最多 12 条") List<ChatMessage> history
) {
    public record ChatMessage(
            @NotBlank(message = "role 不能为空") String role,
            @NotBlank(message = "content 不能为空")
            @Size(max = 3000, message = "历史消息最长 3000 字符") String content
    ) {
    }
}
