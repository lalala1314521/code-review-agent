package io.github.lalala1314521.codereviewagent.review.llm.qianwen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lalala1314521.codereviewagent.config.LlmProperties;
import io.github.lalala1314521.codereviewagent.review.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 通义千问（DashScope）LLM 客户端。
 *
 * <p>DashScope 是自有 API 格式（与 OpenAI 不兼容）：POST {baseUrl}/services/aigc/text-generation/generation，
 * 认证 Bearer，请求体 {model, input: {messages}, parameters}，响应 {output: {choices: [...]}, usage}。
 * Provider 抽象价值：DeepSeek/OpenAI 走 OpenAI 格式（继承 OpenAiCompatibleClient），
 * 通义独立实现，对外都暴露同一 {@link LlmClient#chat}，调用方无感知。
 *
 * <p>本类非 Spring 管理（无 @Component），由 {@code LlmProviderFactory} 创建。
 *
 * @see <a href="https://help.aliyun.com/zh/dashscope/developer-reference/api-details">DashScope API</a>
 */
public class QianwenClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(QianwenClient.class);

    private final RestClient restClient;
    private final LlmProperties.ProviderConfig config;

    public QianwenClient(LlmProperties.ProviderConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader("Authorization", "Bearer " + config.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        DashScopeRequest request = new DashScopeRequest(
                config.model(),
                new Input(List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)
                )),
                new Parameters(
                        config.maxTokens() != null ? config.maxTokens() : 4096,
                        config.temperature() != null ? config.temperature() : 0.2
                )
        );

        log.info("llm call provider=qianwen model={} systemChars={} userChars={}",
                config.model(), systemPrompt.length(), userPrompt.length());

        DashScopeResponse response = restClient.post()
                .uri("/services/aigc/text-generation/generation")
                .body(request)
                .retrieve()
                .body(DashScopeResponse.class);

        if (response == null || response.output() == null
                || response.output().choices() == null || response.output().choices().isEmpty()) {
            log.error("llm empty response provider=qianwen");
            return "";
        }

        String content = response.output().choices().get(0).message().content();
        log.info("llm done provider=qianwen completionChars={} inputTokens={} outputTokens={}",
                content.length(),
                response.usage() != null ? response.usage().inputTokens() : 0,
                response.usage() != null ? response.usage().outputTokens() : 0);
        return content;
    }

    @Override
    public String providerName() {
        return "qianwen";
    }

    // ===== DashScope API 的请求/响应 DTO（结构与 OpenAI 不同） =====

    record DashScopeRequest(
            String model,
            Input input,
            Parameters parameters
    ) {}

    record Input(List<Message> messages) {}

    record Message(String role, String content) {}

    record Parameters(
            @JsonProperty("max_tokens") int maxTokens,
            double temperature
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DashScopeResponse(
            Output output,
            Usage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Output(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {}
}
