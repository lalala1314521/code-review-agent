package io.github.lalala1314521.codereviewagent.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lalala1314521.codereviewagent.api.dto.KpiData;
import io.github.lalala1314521.codereviewagent.persistence.ReviewRecordService;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewRecordEntity;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘聚合服务。
 *
 * <p><b>为什么 KPI 要走 Redis 缓存（TTL 5min）？</b>
 * 管理台首页打开频率高，KPI 涉及 6 次聚合查询（count × 4 + avg × 2），
 * 审查量上去后每次都打 DB 是浪费。KPI 允许分钟级延迟，
 * 缓存 5min 把 QPS 压力从 DB 转移到 Redis（纳秒级）。
 *
 * <p>缓存失效策略：TTL 自然过期即可，不主动失效——
 * 分钟级延迟对 KPI 场景无感知，主动失效反而引入缓存-DB 双写一致性问题。
 */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private static final String KPI_CACHE_KEY = "codereview:kpi:dashboard";
    private static final long KPI_CACHE_TTL_SECONDS = 300;

    private final ReviewRecordMapper recordMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DashboardService(ReviewRecordMapper recordMapper,
                            StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 4 个 KPI 卡数据：缓存优先，miss 则 DB 聚合后回写缓存。
     */
    public KpiData getKpi() {
        KpiData cached = readCache();
        if (cached != null) {
            return cached;
        }

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        long todayCount = countByRange(todayStart, tomorrowStart, null);
        long yesterdayCount = countByRange(yesterdayStart, todayStart, null);
        long avgDuration = avgDurationByRange(todayStart, tomorrowStart);
        long prevAvgDuration = avgDurationByRange(yesterdayStart, todayStart);
        double passRate = passRateByRange(todayStart, tomorrowStart);
        double prevPassRate = passRateByRange(yesterdayStart, todayStart);
        long blockedCount = countByRange(todayStart, tomorrowStart, "BLOCK");

        KpiData kpi = new KpiData(todayCount, yesterdayCount, avgDuration,
                prevAvgDuration, passRate, prevPassRate, blockedCount);
        writeCache(kpi);
        return kpi;
    }

    /**
     * Webhook 连接健康：DB 可达即视为接入正常，附最近一次触发时间供前端展示。
     */
    public Map<String, Object> getWebhookStatus() {
        boolean connected;
        LocalDateTime lastWebhookAt = null;
        try {
            ReviewRecordEntity latest = recordMapper.selectOne(new QueryWrapper<ReviewRecordEntity>()
                    .orderByDesc("triggered_at")
                    .last("LIMIT 1"));
            connected = true;
            if (latest != null) {
                lastWebhookAt = latest.getTriggeredAt();
            }
        } catch (Exception e) {
            log.error("webhook status check failed: {}", e.getMessage());
            connected = false;
        }
        return Map.of(
                "connected", connected,
                "lastWebhookAt", lastWebhookAt == null ? "" : lastWebhookAt.toString()
        );
    }

    /** 区间内记录数；conclusion 为 null 表示不限结论 */
    private long countByRange(LocalDateTime start, LocalDateTime end, String conclusion) {
        QueryWrapper<ReviewRecordEntity> qw = new QueryWrapper<ReviewRecordEntity>()
                .ge("triggered_at", start)
                .lt("triggered_at", end);
        if (conclusion != null) {
            qw.eq("conclusion", conclusion);
        }
        Long count = recordMapper.selectCount(qw);
        return count == null ? 0 : count;
    }

    /** 区间内 DONE 记录的平均耗时 */
    private long avgDurationByRange(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<ReviewRecordEntity> qw = new QueryWrapper<ReviewRecordEntity>()
                .select("COALESCE(AVG(duration_ms), 0) AS avgMs")
                .ge("triggered_at", start)
                .lt("triggered_at", end)
                .eq("status", ReviewRecordService.STATUS_DONE);
        List<Map<String, Object>> rows = recordMapper.selectMaps(qw);
        if (rows.isEmpty() || rows.get(0).get("avgMs") == null) {
            return 0;
        }
        return ((Number) rows.get(0).get("avgMs")).longValue();
    }

    /** 区间内通过率 = APPROVE / DONE（DONE 为 0 时返回 0，避免除零） */
    private double passRateByRange(LocalDateTime start, LocalDateTime end) {
        long done = countByRangeAndStatus(start, end, ReviewRecordService.STATUS_DONE);
        if (done == 0) {
            return 0;
        }
        long approve = countByRangeAndStatusAndConclusion(start, end, "APPROVE");
        return (double) approve / done;
    }

    private long countByRangeAndStatus(LocalDateTime start, LocalDateTime end, String status) {
        Long count = recordMapper.selectCount(new QueryWrapper<ReviewRecordEntity>()
                .ge("triggered_at", start)
                .lt("triggered_at", end)
                .eq("status", status));
        return count == null ? 0 : count;
    }

    private long countByRangeAndStatusAndConclusion(LocalDateTime start, LocalDateTime end, String conclusion) {
        Long count = recordMapper.selectCount(new QueryWrapper<ReviewRecordEntity>()
                .ge("triggered_at", start)
                .lt("triggered_at", end)
                .eq("status", ReviewRecordService.STATUS_DONE)
                .eq("conclusion", conclusion));
        return count == null ? 0 : count;
    }

    private KpiData readCache() {
        try {
            String json = redisTemplate.opsForValue().get(KPI_CACHE_KEY);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, KpiData.class);
        } catch (Exception e) {
            // 缓存读失败降级为直连 DB，不影响功能
            log.warn("kpi cache read failed, fallback to db: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(KpiData kpi) {
        try {
            redisTemplate.opsForValue().set(KPI_CACHE_KEY,
                    objectMapper.writeValueAsString(kpi), KPI_CACHE_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("kpi cache write failed: {}", e.getMessage());
        }
    }
}
