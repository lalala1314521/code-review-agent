package io.github.lalala1314521.codereviewagent.api;

import io.github.lalala1314521.codereviewagent.api.dto.AgentInfo;
import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.config.LlmProperties;
import io.github.lalala1314521.codereviewagent.review.llm.ActiveProviderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Agent 管理 API：列出已配置 Provider，并允许用户切换后续审查使用的活动 Agent。
 *
 * <p>项目默认 Agent 是 DeepSeek（application.yml 的默认值）；用户切换只影响
 * 后续新启动的审查任务，已经运行中的任务保持原 Provider，保证单次结果一致性。</p>
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final LlmProperties properties;
    private final ActiveProviderService activeProviderService;

    public AgentController(LlmProperties properties, ActiveProviderService activeProviderService) {
        this.properties = properties;
        this.activeProviderService = activeProviderService;
    }

    @GetMapping
    public ApiResponse<List<AgentInfo>> list() {
        String active = activeProviderService.getActiveProvider();
        List<AgentInfo> result = properties.providers().entrySet().stream()
                .map(entry -> toInfo(entry.getKey(), entry.getValue(), active))
                .sorted(Comparator.comparing(AgentInfo::defaultAgent).reversed()
                        .thenComparing(AgentInfo::provider))
                .toList();
        return ApiResponse.ok(result);
    }

    @GetMapping("/active")
    public ApiResponse<AgentInfo> active() {
        String active = activeProviderService.getActiveProvider();
        return ApiResponse.ok(toInfo(active, properties.providers().get(active), active));
    }

    @PutMapping("/active")
    public ApiResponse<AgentInfo> switchActive(@Valid @RequestBody SwitchAgentRequest request) {
        String active = activeProviderService.setActiveProvider(request.provider());
        return ApiResponse.ok(toInfo(active, properties.providers().get(active), active));
    }

    private AgentInfo toInfo(String provider, LlmProperties.ProviderConfig config, String active) {
        return new AgentInfo(
                provider,
                displayName(provider),
                // 生效模型名（DB 覆盖优先），非 yml 固定值
                activeProviderService.modelName(provider),
                provider.equals(active),
                activeProviderService.isAvailable(provider),
                provider.equals(activeProviderService.getDefaultProvider())
        );
    }

    private String displayName(String provider) {
        return switch (provider.toLowerCase()) {
            case "deepseek" -> "DeepSeek";
            case "qianwen" -> "通义千问";
            case "openai" -> "OpenAI";
            default -> provider;
        };
    }

    public record SwitchAgentRequest(@NotBlank(message = "provider 不能为空") String provider) {
    }
}
