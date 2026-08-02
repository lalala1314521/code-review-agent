package io.github.lalala1314521.codereviewagent.api;

import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.review.llm.ActiveProviderService;
import io.github.lalala1314521.codereviewagent.review.llm.ProviderConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * LLM Provider 配置管理 API。
 *
 * <p>GET    /api/v1/providers              配置列表（key 掩码，绝不明文）
 * PUT    /api/v1/providers/{provider}    更新配置（apiKey 传空 = 不修改）
 * DELETE /api/v1/providers/{provider}    恢复出厂（删 DB 覆盖，回 yml 默认）
 *
 * <p><b>安全边界（面试问答要点）：</b>
 * <ul>
 *   <li>key 加密存 DB（AES-GCM，主密钥走环境变量），接口只回掩码</li>
 *   <li>本系统暂无鉴权体系——这些端点仅限内网/个人部署使用；
 *       生产环境必须在网关或拦截器加管理员鉴权（V3 规划），
 *       否则任何人可改 base-url 把代码 diff 导向恶意端点（SSRF 风险）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/providers")
public class ProviderConfigController {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfigController.class);

    private final ProviderConfigService providerConfigService;
    private final ActiveProviderService activeProviderService;

    public ProviderConfigController(ProviderConfigService providerConfigService,
                                    ActiveProviderService activeProviderService) {
        this.providerConfigService = providerConfigService;
        this.activeProviderService = activeProviderService;
    }

    /**
     * 配置列表（掩码视图，含 active 标记）。
     */
    @GetMapping
    public ApiResponse<List<ProviderConfigService.ProviderConfigView>> list() {
        return ApiResponse.ok(providerConfigService.listView(activeProviderService.getActiveProvider()));
    }

    /**
     * 更新配置（保存即热刷新 client，无需重启）。
     */
    @PutMapping("/{provider}")
    public ApiResponse<ProviderConfigService.ProviderConfigView> update(
            @PathVariable String provider,
            @RequestBody ProviderConfigService.ProviderConfigUpdateRequest req) {
        log.info("provider config update request: {}", provider);
        return ApiResponse.ok(providerConfigService.update(provider, req, activeProviderService.getActiveProvider()));
    }

    /**
     * 恢复出厂配置（删除 DB 覆盖行）。
     */
    @DeleteMapping("/{provider}")
    public ApiResponse<Void> reset(@PathVariable String provider) {
        providerConfigService.resetToDefault(provider, activeProviderService.getActiveProvider());
        return ApiResponse.ok(null);
    }
}
