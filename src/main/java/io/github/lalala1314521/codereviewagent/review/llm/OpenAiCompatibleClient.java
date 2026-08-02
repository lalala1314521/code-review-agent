package io.github.lalala1314521.codereviewagent.review.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * OpenAI 兼容格式的 LLM 客户端抽象基类。
 *
 * <p>DeepSeek / OpenAI / 通义（compatible-mode）共用 Chat Completions 格式：
 * POST {baseUrl}/chat/completions，认证 Bearer，请求体 {model, messages, max_tokens, temperature}，
 * 响应 {choices: [{message: {content}}], usage}。公共调用逻辑放基类，子类只提供 providerName() 与配置（模板方法模式）。
 *
 * <p>本类非 Spring 管理（无 @Component），由 {@link LlmProviderFactory} 通过 new 创建并传入配置。
 */
public abstract class OpenAiCompatibleClient implements LlmClient {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final RestClient restClient;
    private final LlmProperties.ProviderConfig config;

    protected OpenAiCompatibleClient(LlmProperties.ProviderConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader("Authorization", "Bearer " + config.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        ChatRequest request = new ChatRequest(
                config.model(),
                List.of(
                        new Message("system", systemPrompt),
                        new Message("user", userPrompt)
                ),
                config.maxTokens() != null ? config.maxTokens() : 4096,
                config.temperature() != null ? config.temperature() : 0.2
        );

        log.info("llm call provider={} model={} systemChars={} userChars={}",
                providerName(), config.model(), systemPrompt.length(), userPrompt.length());

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (HttpClientErrorException e) {
            // 4xx：透传 LLM 真实原因（模型名失效/key 无效/额度不足），便于用户自助定位
            String reason = extractErrorMessage(e.getResponseBodyAsString());
            log.error("llm 4xx provider={} status={} reason={}", providerName(), e.getStatusCode(), reason);
            throw new BizException(400, providerName() + " 调用失败：" + reason);
        } catch (HttpServerErrorException e) {
            // 5xx：LLM 服务端故障
            log.error("llm 5xx provider={} status={}", providerName(), e.getStatusCode());
            throw new BizException(502, providerName() + " 服务端异常（" + e.getStatusCode() + "），请稍后重试");
        } catch (ResourceAccessException e) {
            // 网络层：base-url 配错 / 断网 / 代理失效
            log.error("llm network error provider={}: {}", providerName(), e.getMessage());
            throw new BizException(502, providerName() + " 网络不可达，请检查网络或 Base URL 配置");
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            log.error("llm empty response provider={}", providerName());
            return "";
        }

        String content = response.choices().get(0).message().content();
        log.info("llm done provider={} completionChars={} promptTokens={} completionTokens={}",
                providerName(), content.length(),
                response.usage() != null ? response.usage().promptTokens() : 0,
                response.usage() != null ? response.usage().completionTokens() : 0);
        return content;
    }

    // ===== OpenAI 兼容 API 的请求/响应 DTO =====

    /** 共享 JSON 解析器（client 非 Spring 管理故静态持有） */
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    /**
     * 从 OpenAI 兼容错误响应体提取 error.message；解析失败返回截断原文。
     */
    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "未知错误";
        }
        try {
            var node = ERROR_MAPPER.readTree(responseBody).path("error").path("message");
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        } catch (Exception ignored) {
            // 解析失败，返回截断原文
        }
        return responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody;
    }

    protected record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature
    ) {}

    protected record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record ChatResponse(
            List<Choice> choices,
            Usage usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record Choice(
            Message message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    protected record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens
    ) {}
}
