package io.github.lalala1314521.codereviewagent.review.llm.deepseek;

import io.github.lalala1314521.codereviewagent.config.LlmProperties;
import io.github.lalala1314521.codereviewagent.review.llm.OpenAiCompatibleClient;

/**
 * DeepSeek LLM 客户端。
 *
 * <p>DeepSeek API 完全兼容 OpenAI Chat Completions 格式，
 * 所有调用逻辑在 {@link OpenAiCompatibleClient} 基类中，这里只提供 Provider 标识。
 *
 * <p>选择 DeepSeek 作为默认 Provider 的理由：性价比极高（约 GPT-4o 的 1/100）、
 * 中文场景表现强、国内访问稳定免代理。
 *
 * <p>注意：本类不由 Spring 管理（无 @Component），由 {@code LlmProviderFactory} 创建。
 */
public class DeepSeekClient extends OpenAiCompatibleClient {

    public DeepSeekClient(LlmProperties.ProviderConfig config) {
        super(config);
    }

    @Override
    public String providerName() {
        return "deepseek";
    }
}
