package io.github.lalala1314521.codereviewagent.review.llm;

/**
 * LLM 客户端抽象：按 yml 配置切换实现（{@code LlmProviderFactory}）。
 */
public interface LlmClient {

    /**
     * 调用 LLM 对话。
     *
     * @param systemPrompt 系统提示词（角色 + 输出格式要求）
     * @param userPrompt   用户提示词（MR diff 内容）
     * @return LLM 生成的文本
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * @return Provider 标识，如 "deepseek" / "qianwen" / "openai"
     */
    String providerName();
}
