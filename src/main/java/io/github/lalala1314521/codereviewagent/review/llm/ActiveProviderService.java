package io.github.lalala1314521.codereviewagent.review.llm;

import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 运行时活动 Provider 管理器。
 *
 * <p>系统默认仍由 {@code llm.default-provider} 决定（项目默认值为 deepseek）；
 * 管理台切换后写入 Redis，并同步更新进程内 AtomicReference。Redis 不可用时
 * 自动降级为内存状态，不阻断审查主链路。</p>
 *
 * <p>一次审查开始后应把 {@link #getActiveClient()} 返回值保存为局部变量，
 * 这样用户在审查过程中切换 Agent 不会造成同一任务前后使用两个模型。</p>
 */
@Service
public class ActiveProviderService {

    private static final Logger log = LoggerFactory.getLogger(ActiveProviderService.class);
    private static final String REDIS_KEY = "codereview:llm:active-provider";

    private final LlmProviderFactory providerFactory;
    private final LlmProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ProviderConfigService providerConfigService;
    private final AtomicReference<String> activeProvider;

    public ActiveProviderService(LlmProviderFactory providerFactory,
                                 LlmProperties properties,
                                 StringRedisTemplate redisTemplate,
                                 ProviderConfigService providerConfigService) {
        this.providerFactory = providerFactory;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.providerConfigService = providerConfigService;
        this.activeProvider = new AtomicReference<>(properties.defaultProvider());
        restoreFromRedis();
    }

    public String getActiveProvider() {
        // 多实例部署：按需读 Redis，其他实例的切换结果下次任务前可见
        try {
            String saved = redisTemplate.opsForValue().get(REDIS_KEY);
            if (StringUtils.hasText(saved)
                    && providerFactory.availableProviders().contains(saved)
                    && isAvailable(saved)) {
                activeProvider.set(saved);
            }
        } catch (Exception e) {
            log.debug("read active provider from redis failed, use memory value: {}", e.getMessage());
        }
        return activeProvider.get();
    }
    public String getDefaultProvider() {
        return properties.defaultProvider();
    }

    public LlmClient getActiveClient() {
        return providerFactory.get(getActiveProvider());
    }

    public LlmClient getClient(String provider) {
        if (!StringUtils.hasText(provider)) {
            return getActiveClient();
        }
        return providerFactory.get(provider);
    }

    public String setActiveProvider(String provider) {
        validateSelectable(provider);
        activeProvider.set(provider);
        try {
            redisTemplate.opsForValue().set(REDIS_KEY, provider);
        } catch (Exception e) {
            log.warn("persist active llm provider failed, keep in memory: {}", e.getMessage());
        }
        log.info("active llm provider switched to {}", provider);
        return provider;
    }

    public boolean isAvailable(String provider) {
        // 委托 ProviderConfigService：DB 覆盖配置也算（管理台配的 key 同样可用）
        return providerConfigService.isAvailable(provider);
    }

    public String modelName(String provider) {
        return providerConfigService.modelName(provider);
    }

    private void validateSelectable(String provider) {
        if (!StringUtils.hasText(provider) || !providerFactory.availableProviders().contains(provider)) {
            throw new BizException(400, "未知 Agent Provider: " + provider);
        }
        if (!isAvailable(provider)) {
            throw new BizException(400, "Agent " + provider + " 未配置 API Key，暂不可用");
        }
    }

    private void restoreFromRedis() {
        try {
            String saved = redisTemplate.opsForValue().get(REDIS_KEY);
            if (StringUtils.hasText(saved)
                    && providerFactory.availableProviders().contains(saved)
                    && isAvailable(saved)) {
                activeProvider.set(saved);
                log.info("active llm provider restored from redis: {}", saved);
            }
        } catch (Exception e) {
            log.warn("restore active llm provider failed, use default {}: {}",
                    properties.defaultProvider(), e.getMessage());
        }
    }
}
