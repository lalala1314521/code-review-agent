package io.github.lalala1314521.codereviewagent.review.llm;

import io.github.lalala1314521.codereviewagent.common.crypto.CryptoUtil;
import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.config.LlmProperties;
import io.github.lalala1314521.codereviewagent.persistence.entity.ProviderConfigEntity;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ProviderConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider 运行时配置服务：管理台改 LLM 配置，不用改 yml 重启。
 *
 * <p>配置合并优先级：DB（管理台改的）> yml（出厂默认），删 DB 行即回 yml 状态。
 *
 * <p>安全设计：API Key AES-GCM 加密存 DB（主密钥走环境变量 LLM_CONFIG_SECRET）；
 * 接口永不回传明文（GET 只回掩码 sk-**** + 后 4 位，PUT 传空 = 不修改）；
 * 配置变更即热刷新 client 实例（Factory.rebuild），不用重启。
 */
@Service
public class ProviderConfigService {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfigService.class);

    private final LlmProperties ymlProperties;
    private final ProviderConfigMapper configMapper;
    private final CryptoUtil cryptoUtil;
    private final LlmProviderFactory providerFactory;

    public ProviderConfigService(LlmProperties ymlProperties,
                                 ProviderConfigMapper configMapper,
                                 CryptoUtil cryptoUtil,
                                 LlmProviderFactory providerFactory) {
        this.ymlProperties = ymlProperties;
        this.configMapper = configMapper;
        this.cryptoUtil = cryptoUtil;
        this.providerFactory = providerFactory;
    }

    /**
     * 某 Provider 的生效配置（DB 覆盖 yml）。
     */
    public LlmProperties.ProviderConfig effectiveConfig(String provider) {
        LlmProperties.ProviderConfig yml = ymlProperties.providers().get(provider);
        if (yml == null) {
            throw new BizException(404, "未知 provider: " + provider);
        }
        ProviderConfigEntity db = configMapper.selectById(provider);
        if (db == null) {
            return yml;
        }
        return new LlmProperties.ProviderConfig(
                StringUtils.hasText(db.getBaseUrl()) ? db.getBaseUrl() : yml.baseUrl(),
                decryptIfPresent(db.getApiKeyEnc(), yml.apiKey()),
                StringUtils.hasText(db.getModel()) ? db.getModel() : yml.model(),
                db.getMaxTokens() != null ? db.getMaxTokens() : yml.maxTokens(),
                db.getTemperature() != null ? db.getTemperature().doubleValue() : yml.temperature()
        );
    }

    /**
     * 是否可用（生效配置里有 API Key）——供 ActiveProviderService 委托。
     */
    public boolean isAvailable(String provider) {
        try {
            LlmProperties.ProviderConfig config = effectiveConfig(provider);
            return StringUtils.hasText(config.apiKey());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生效模型名——供 ActiveProviderService 委托。
     */
    public String modelName(String provider) {
        try {
            return effectiveConfig(provider).model();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 管理台展示视图（掩码，绝不含明文 key）。
     */
    public List<ProviderConfigView> listView(String activeProvider) {
        List<ProviderConfigView> views = new ArrayList<>();
        for (String provider : providerFactory.availableProviders()) {
            LlmProperties.ProviderConfig effective = effectiveConfig(provider);
            ProviderConfigEntity db = configMapper.selectById(provider);
            boolean hasDbKey = db != null && StringUtils.hasText(db.getApiKeyEnc());
            boolean configured = StringUtils.hasText(effective.apiKey());
            views.add(new ProviderConfigView(
                    provider,
                    effective.baseUrl(),
                    maskKey(db, configured),
                    effective.model(),
                    effective.maxTokens(),
                    effective.temperature(),
                    configured,
                    hasDbKey,
                    provider.equals(activeProvider)
            ));
        }
        return views;
    }

    /**
     * 更新配置（管理台保存）。
     *
     * <p>apiKey 传空/掩码值 = 不修改；传新值则加密入库。
     * 保存后立即热刷新该 Provider 的 client 实例。
     */
    public ProviderConfigView update(String provider, ProviderConfigUpdateRequest req, String activeProvider) {
        LlmProperties.ProviderConfig yml = ymlProperties.providers().get(provider);
        if (yml == null) {
            throw new BizException(404, "未知 provider: " + provider);
        }

        ProviderConfigEntity entity = configMapper.selectById(provider);
        boolean isNew = entity == null;
        if (isNew) {
            entity = new ProviderConfigEntity();
            entity.setProvider(provider);
        }

        if (StringUtils.hasText(req.baseUrl())) {
            entity.setBaseUrl(req.baseUrl().trim());
        }
        if (StringUtils.hasText(req.model())) {
            entity.setModel(req.model().trim());
        }
        if (req.maxTokens() != null) {
            entity.setMaxTokens(req.maxTokens());
        }
        if (req.temperature() != null) {
            entity.setTemperature(BigDecimal.valueOf(req.temperature()));
        }
        // key：非空且非掩码样式才更新（防误把掩码当真 key 存了）
        if (StringUtils.hasText(req.apiKey()) && !req.apiKey().contains("****")) {
            String plainKey = req.apiKey().trim();
            entity.setApiKeyEnc(cryptoUtil.encrypt(plainKey));
            entity.setApiKeyTail(tail(plainKey));
        }

        if (isNew) {
            configMapper.insert(entity);
        } else {
            configMapper.updateById(entity);
        }
        log.info("provider config updated: {} (baseUrl/model/key{} )", provider,
                StringUtils.hasText(req.apiKey()) && !req.apiKey().contains("****") ? "已更新" : "未变");

        // 热刷新 client：下一次调用即新配置，无需重启
        providerFactory.rebuild(provider, effectiveConfig(provider));
        log.info("provider client rebuilt with new config: {}", provider);

        return listView(activeProvider).stream()
                .filter(v -> v.provider().equals(provider))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 恢复出厂：删除 DB 覆盖行，回到 yml 默认。
     */
    public void resetToDefault(String provider, String activeProvider) {
        configMapper.deleteById(provider);
        providerFactory.rebuild(provider, effectiveConfig(provider));
        log.info("provider config reset to yml default: {}", provider);
    }

    // ===== 内部工具 =====

    private String decryptIfPresent(String enc, String fallback) {
        if (!StringUtils.hasText(enc)) {
            return fallback;
        }
        try {
            return cryptoUtil.decrypt(enc);
        } catch (Exception e) {
            log.error("decrypt provider key failed, fallback to yml: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * 掩码：sk-**** 后4位；DB 无 key 但 yml 有 key 时显示 yml 来源标记。
     */
    private String maskKey(ProviderConfigEntity db, boolean configured) {
        if (!configured) {
            return "";
        }
        if (db != null && StringUtils.hasText(db.getApiKeyTail())) {
            return "sk-****" + db.getApiKeyTail();
        }
        // yml 配置的 key：不暴露任何位，只标记来源
        return "sk-********（来自配置文件）";
    }

    private String tail(String key) {
        return key.length() <= 4 ? key : key.substring(key.length() - 4);
    }

    // ===== DTO =====

    /**
     * 管理台展示视图（绝无明文 key）。
     */
    public record ProviderConfigView(
            String provider,
            String baseUrl,
            String apiKeyMasked,
            String model,
            Integer maxTokens,
            Double temperature,
            boolean configured,
            boolean keyFromDatabase,
            boolean active
    ) {}

    /**
     * 更新请求（apiKey 传空 = 不修改）。
     */
    public record ProviderConfigUpdateRequest(
            String baseUrl,
            String apiKey,
            String model,
            Integer maxTokens,
            Double temperature
    ) {}
}
