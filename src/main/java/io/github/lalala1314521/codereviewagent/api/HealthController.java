package io.github.lalala1314521.codereviewagent.api;

import io.github.lalala1314521.codereviewagent.config.GitLabProperties;
import io.github.lalala1314521.codereviewagent.config.LlmProperties;
import io.github.lalala1314521.codereviewagent.review.llm.ActiveProviderService;
import io.github.lalala1314521.codereviewagent.review.llm.LlmProviderFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 健康检查端点：展示 GitLab 与多 Agent Provider 的配置状态。 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final GitLabProperties gitLabProps;
    private final LlmProperties llmProps;
    private final LlmProviderFactory llmProviderFactory;
    private final ActiveProviderService activeProviderService;

    public HealthController(GitLabProperties gitLabProps,
                            LlmProperties llmProps,
                            LlmProviderFactory llmProviderFactory,
                            ActiveProviderService activeProviderService) {
        this.gitLabProps = gitLabProps;
        this.llmProps = llmProps;
        this.llmProviderFactory = llmProviderFactory;
        this.activeProviderService = activeProviderService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", isReady() ? "UP" : "CONFIG_MISSING");
        result.put("gitlab", Map.of(
                "baseUrl", gitLabProps.baseUrl(),
                "tokenConfigured", !isBlank(gitLabProps.token()),
                "webhookSecretConfigured", !isBlank(gitLabProps.webhookSecret())
        ));
        result.put("llm", buildLlmInfo());
        return result;
    }

    private Map<String, Object> buildLlmInfo() {
        Map<String, Object> llmInfo = new LinkedHashMap<>();
        String defaultProvider = llmProps.defaultProvider();
        String activeProvider = activeProviderService.getActiveProvider();
        llmInfo.put("defaultProvider", defaultProvider);
        llmInfo.put("activeProvider", activeProvider);
        llmInfo.put("availableProviders", llmProviderFactory.availableProviders());

        Map<String, Object> providerDetails = new LinkedHashMap<>();
        llmProps.providers().forEach((name, config) -> providerDetails.put(name, Map.of(
                "model", config.model() != null ? config.model() : "unknown",
                "apiKeyConfigured", !isBlank(config.apiKey()),
                "isDefault", name.equals(defaultProvider),
                "isActive", name.equals(activeProvider)
        )));
        llmInfo.put("providers", providerDetails);
        return llmInfo;
    }

    private boolean isReady() {
        var activeConfig = llmProps.providers().get(activeProviderService.getActiveProvider());
        boolean llmReady = activeConfig != null && !isBlank(activeConfig.apiKey());
        return !isBlank(gitLabProps.token())
                && !isBlank(gitLabProps.webhookSecret())
                && llmReady;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}