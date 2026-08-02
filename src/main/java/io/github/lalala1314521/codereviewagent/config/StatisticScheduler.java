package io.github.lalala1314521.codereviewagent.config;

import io.github.lalala1314521.codereviewagent.persistence.StatisticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 审查统计定时聚合任务。
 *
 * <p><b>为什么每天 01:00 而不是 00:00？</b>整点是所有定时任务的 instinct 选择，
 * 容易和其他系统的任务撞车（DB 瞬时压力）；错开 1 小时是运维惯例。
 *
 * <p><b>为什么聚合"昨天"？</b>昨天 24 小时的数据已完整封存，聚合结果不会变；
 * 今天的数据还在流动，由 stats 接口实时计算（冷热分离）。
 *
 * <p><b>失败怎么办？</b>不用处理。聚合失败只影响报表新鲜度，明天任务会再次执行，
 * 幂等 upsert 保证重跑结果正确；需要立即补数时调 POST /api/v1/history/stats/rebuild。
 */
@Component
@EnableScheduling
public class StatisticScheduler {

    private static final Logger log = LoggerFactory.getLogger(StatisticScheduler.class);

    private final StatisticService statisticService;

    public StatisticScheduler(StatisticService statisticService) {
        this.statisticService = statisticService;
    }

    /**
     * 每日 01:00 聚合前一天数据（cron：秒 分 时 日 月 周）。
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void aggregateYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            statisticService.aggregateDate(yesterday);
        } catch (Exception e) {
            // 失败仅告警——明天会重跑，幂等保证正确性
            log.error("daily statistic aggregation failed date={}: {}", yesterday, e.getMessage(), e);
        }
    }
}
