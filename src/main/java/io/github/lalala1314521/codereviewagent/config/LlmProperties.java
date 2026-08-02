package io.github.lalala1314521.codereviewagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * LLM 多 Provider 配置（application.yml 的 llm.* 节点）。
 *
 * <p>示例：
 * <pre>
 * llm:
 *   default-provider: deepseek
 *   providers:
 *     deepseek:
 *       base-url: https://api.deepseek.com/v1
 *       api-key: xxx
 *       model: deepseek-chat
 * </pre>
 * 新增 Provider 只需在 yml 加配置 + Factory 加一个 case，不用改接口。
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String defaultProvider,
        Map<String, ProviderConfig> providers
) {

    /**
     * 单个 Provider 的配置。
     */
    public record ProviderConfig(
            String baseUrl,
            String apiKey,
            String model,
            Integer maxTokens,
            Double temperature
    ) {}
}
