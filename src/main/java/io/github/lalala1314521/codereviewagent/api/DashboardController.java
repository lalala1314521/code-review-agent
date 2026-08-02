package io.github.lalala1314521.codereviewagent.api;

import io.github.lalala1314521.codereviewagent.api.dto.KpiData;
import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 仪表盘 API（方案设计 12.2）。
 *
 * <p>GET /api/v1/dashboard/kpi             4 个 KPI 卡数据（Redis 缓存 5min）
 * GET /api/v1/dashboard/webhook-status     Webhook 连接健康
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/kpi")
    public ApiResponse<KpiData> kpi() {
        return ApiResponse.ok(dashboardService.getKpi());
    }

    @GetMapping("/webhook-status")
    public ApiResponse<Map<String, Object>> webhookStatus() {
        return ApiResponse.ok(dashboardService.getWebhookStatus());
    }
}
