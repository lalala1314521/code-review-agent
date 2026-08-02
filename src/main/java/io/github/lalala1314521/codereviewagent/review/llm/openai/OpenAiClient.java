package io.github.lalala1314521.codereviewagent.review.llm.openai;

import io.github.lalala1314521.codereviewagent.config.LlmProperties;
import io.github.lalala1314521.codereviewagent.review.llm.OpenAiCompatibleClient;

/**
 * OpenAI LLM 客户端。
 *
 * <p>OpenAI 是 Chat Completions 格式的"原厂"，
 * 所有调用逻辑在 {@link OpenAiCompatibleClient} 基类中，这里只提供 Provider 标识。
 *
 * <p>注意：本类不由 Spring 管理（无 @Component），由 {@code LlmProviderFactory} 创建。
 * 国内访问 OpenAI 需要代理，一般作为备选 Provider 而非默认。
 */
public class OpenAiClient extends OpenAiCompatibleClient {

    public OpenAiClient(LlmProperties.ProviderConfig config) {
        super(config);
    }

    @Override
    public String providerName() {
        return "openai";
    }
}
