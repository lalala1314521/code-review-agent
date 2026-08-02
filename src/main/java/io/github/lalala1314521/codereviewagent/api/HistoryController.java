package io.github.lalala1314521.codereviewagent.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.common.api.PageResult;
import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.persistence.StatisticService;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewRecordEntity;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewStatisticEntity;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewRecordMapper;
import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewStatisticMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史记录 API（方案设计 12.6）。
 *
 * <p>GET  /api/v1/history                分页查询（repo / 时间范围筛选）
 * GET  /api/v1/history/stats            按日聚合统计（折线图数据，冷热分离）
 * POST /api/v1/history/stats/rebuild    手动补数（重聚合指定日期，幂等）
 *
 * <p>与 /reviews 的区别：reviews 面向"队列"（关注待处理/进行中的 MR），
 * history 面向"复盘"（全量历史 + 趋势分析）。
 */
@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final ReviewRecordMapper recordMapper;
    private final ReviewStatisticMapper statisticMapper;
    private final StatisticService statisticService;

    public HistoryController(ReviewRecordMapper recordMapper,
                             ReviewStatisticMapper statisticMapper,
                             StatisticService statisticService) {
        this.recordMapper = recordMapper;
        this.statisticMapper = statisticMapper;
        this.statisticService = statisticService;
    }

    /**
     * 历史分页查询。
     *
     * @param repo  仓库路径（模糊匹配，如 "backend" 命中 "group/backend"）
     * @param start 起始日期（含），格式 yyyy-MM-dd
     * @param end   截止日期（含），格式 yyyy-MM-dd
     */
    @GetMapping
    public ApiResponse<PageResult<ReviewRecordEntity>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        if (size > 100) {
            size = 100;
        }
        QueryWrapper<ReviewRecordEntity> qw = new QueryWrapper<ReviewRecordEntity>()
                .like(StringUtils.hasText(repo), "repo_path", repo)
                .eq(StringUtils.hasText(branch), "source_branch", branch)
                .ge(StringUtils.hasText(start), "triggered_at", parseDate(start, true))
                .lt(StringUtils.hasText(end), "triggered_at", parseDate(end, false))
                .orderByDesc("triggered_at");

        Page<ReviewRecordEntity> result = recordMapper.selectPage(new Page<>(page, size), qw);
        return ApiResponse.ok(PageResult.of(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getRecords()));
    }

    /**
     * 按日聚合统计（折线图数据源）——冷热分离。
     *
     * <ul>
     *   <li><b>冷数据</b>（昨天及以前）：查 review_statistic 预聚合表，
     *       每日凌晨定时任务已聚合好，几十行小表查询，毫秒级</li>
     *   <li><b>热数据</b>（今天）：预聚合表还没有（定时任务凌晨才跑昨天），
     *       实时 GROUP BY review_record 补齐——当天数据量小，实时算得起</li>
     *   <li><b>降级</b>：某天预聚合缺失（定时任务失败/新部署），对该天实时计算兜底，
     *       报表永远有数据，只是慢一点</li>
     * </ul>
     */
    @GetMapping("/stats")
    public ApiResponse<List<Map<String, Object>>> stats(@RequestParam(defaultValue = "30") int days) {
        if (days > 365) {
            days = 365;
        }
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(days);

        // 冷：预聚合表（since ≤ stat_date < today）
        List<ReviewStatisticEntity> coldRows = statisticMapper.selectList(
                new QueryWrapper<ReviewStatisticEntity>()
                        .ge("stat_date", since)
                        .lt("stat_date", today)
                        .orderByAsc("stat_date"));

        Map<String, Map<String, Object>> byDate = new HashMap<>();
        for (ReviewStatisticEntity e : coldRows) {
            byDate.computeIfAbsent(e.getStatDate().toString(), k -> newStatRow(k))
                    .putAll(toStatMap(e));
        }

        // 热 + 降级兜底：实时 GROUP BY（今天必算；冷数据缺失的日期一并补）
        List<Map<String, Object>> liveRows = recordMapper.selectMaps(new QueryWrapper<ReviewRecordEntity>()
                .select("DATE(triggered_at) AS statDate",
                        "COUNT(*) AS totalCount",
                        "SUM(CASE WHEN conclusion = 'APPROVE' THEN 1 ELSE 0 END) AS approveCount",
                        "SUM(CASE WHEN conclusion = 'NEEDS_FIX' THEN 1 ELSE 0 END) AS needsFixCount",
                        "SUM(CASE WHEN conclusion = 'BLOCK' THEN 1 ELSE 0 END) AS blockCount",
                        "COALESCE(AVG(duration_ms), 0) AS avgDurationMs")
                .ge("triggered_at", since.atStartOfDay())
                .eq("status", "DONE")
                .groupBy("DATE(triggered_at)")
                .orderByAsc("statDate"));
        for (Map<String, Object> row : liveRows) {
            String date = String.valueOf(row.get("statDate"));
            // 历史日期以预聚合为准；今天和缺失日期用实时值
            if (!byDate.containsKey(date) || date.equals(today.toString())) {
                byDate.put(date, newStatRow(date));
                byDate.get(date).putAll(row);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(byDate.values());
        result.sort(Comparator.comparing(m -> String.valueOf(m.get("statDate"))));
        return ApiResponse.ok(result);
    }

    /**
     * 手动补数：重聚合指定日期（幂等，可反复调用）。
     *
     * <p>用途：定时任务失败后的补数、新环境历史回填、联调演示。
     */
    @PostMapping("/stats/rebuild")
    public ApiResponse<Map<String, Object>> rebuild(@RequestParam String date) {
        LocalDate d;
        try {
            d = LocalDate.parse(date);
        } catch (Exception e) {
            throw new BizException(400, "date 格式应为 yyyy-MM-dd: " + date);
        }
        if (d.isAfter(LocalDate.now())) {
            throw new BizException(400, "不能聚合未来日期: " + date);
        }
        int rows = statisticService.aggregateDate(d);
        return ApiResponse.ok(Map.of("date", d.toString(), "affectedRows", rows));
    }

    private Map<String, Object> newStatRow(String date) {
        Map<String, Object> row = new HashMap<>();
        row.put("statDate", date);
        row.put("totalCount", 0);
        row.put("approveCount", 0);
        row.put("needsFixCount", 0);
        row.put("blockCount", 0);
        row.put("avgDurationMs", 0);
        return row;
    }

    private Map<String, Object> toStatMap(ReviewStatisticEntity e) {
        Map<String, Object> map = new HashMap<>();
        map.put("statDate", e.getStatDate().toString());
        map.put("totalCount", e.getTotalCount());
        map.put("approveCount", e.getApproveCount());
        map.put("needsFixCount", e.getNeedsFixCount());
        map.put("blockCount", e.getBlockCount());
        map.put("avgDurationMs", e.getAvgDurationMs());
        return map;
    }

    /** start → 当天 00:00:00（含）；end → 次日 00:00:00（不含） */
    private String parseDate(String date, boolean isStart) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        LocalDate d = LocalDate.parse(date);
        return (isStart ? d.atStartOfDay() : d.plusDays(1).atStartOfDay()).toString();
    }
}
