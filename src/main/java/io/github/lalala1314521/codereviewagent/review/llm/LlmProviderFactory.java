package io.github.lalala1314521.codereviewagent.review.llm;

import io.github.lalala1314521.codereviewagent.config.LlmProperties;
import io.github.lalala1314521.codereviewagent.review.llm.deepseek.DeepSeekClient;
import io.github.lalala1314521.codereviewagent.review.llm.openai.OpenAiClient;
import io.github.lalala1314521.codereviewagent.review.llm.qianwen.QianwenClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * LLM Provider 工厂（工厂 + 策略模式）：启动时按 {@code llm.providers} 创建所有 Provider 实例缓存，
 * {@link #getDefault()} 返回默认实例，{@link #get(String)} 按名字取（支持运行时切换/降级）。
 *
 * <p>新增 Provider：写一个 {@link LlmClient} 实现类 + yml 加配置 + {@link #createClient} 加一个 case，
 * 接口与调用方不用改（开闭原则）。
 */
@Component
public class LlmProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderFactory.class);

    private final Map<String, LlmClient> providers;
    private final String defaultProvider;

    public LlmProviderFactory(LlmProperties props) {
        this.defaultProvider = props.defaultProvider();
        this.providers = new HashMap<>();

        if (props.providers() == null || props.providers().isEmpty()) {
            throw new IllegalStateException("llm.providers 配置为空，至少需要一个 Provider");
        }

        props.providers().forEach((name, config) -> {
            LlmClient client = createClient(name, config);
            providers.put(name, client);
            log.info("llm provider registered: {} (model={}, baseUrl={})",
                    name, config.model(), config.baseUrl());
        });

        if (!providers.containsKey(defaultProvider)) {
            throw new IllegalStateException(
                    "llm.default-provider=" + defaultProvider + " 未在 llm.providers 中配置");
        }
        log.info("llm default provider: {}", defaultProvider);
    }

    /**
     * 获取默认 Provider（按 yml 配置）。
     */
    public LlmClient getDefault() {
        return providers.get(defaultProvider);
    }

    /**
     * 按名字获取 Provider（支持运行时切换 / 降级 / AB 测试）。
     */
    public LlmClient get(String name) {
        LlmClient client = providers.get(name);
        if (client == null) {
            throw new IllegalArgumentException("unknown llm provider: " + name
                    + ", available: " + providers.keySet());
        }
        return client;
    }

    /**
     * 所有已注册的 Provider 名（用于健康检查 / 管理台展示）。
     */
    public Set<String> availableProviders() {
        return providers.keySet();
    }

    /**
     * 用热配置重建某个 Provider 的 client 实例（管理台改配置后调用）。
     *
     * <p>client 的 base-url/api-key/model 在构造时固化，配置变更必须重建实例。
     * 调用方（ReviewEngine/ChatService）每次任务现取 client，重建后自动生效，无需重启。
     */
    public synchronized void rebuild(String provider, LlmProperties.ProviderConfig config) {
        LlmClient client = createClient(provider, config);
        providers.put(provider, client);
        log.info("llm provider rebuilt: {} (model={}, baseUrl={})", provider, config.model(), config.baseUrl());
    }

    /**
     * 按名字创建对应的 Provider 实例（工厂方法）。
     */
    private LlmClient createClient(String name, LlmProperties.ProviderConfig config) {
        return switch (name.toLowerCase()) {
            case "deepseek" -> new DeepSeekClient(config);
            case "qianwen" -> new QianwenClient(config);
            case "openai" -> new OpenAiClient(config);
            default -> throw new IllegalArgumentException("unsupported llm provider: " + name);
        };
    }
}
