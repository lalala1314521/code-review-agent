package io.github.lalala1314521.codereviewagent.persistence;

import io.github.lalala1314521.codereviewagent.persistence.mapper.ReviewStatisticMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 审查统计预聚合服务。
 *
 * <p>职责：把 review_record 的明细数据按日聚合成 review_statistic，
 * 供仪表盘/历史图表直接查小表，避免全表扫描明细。
 *
 * <p><b>聚合任务失败不影响任何在线功能</b>——stats 接口对缺失日期降级实时计算，
 * 定时任务挂了最多报表慢点，明天重跑补上即可（幂等 upsert 保证重跑正确）。
 */
@Service
public class StatisticService {

    private static final Logger log = LoggerFactory.getLogger(StatisticService.class);

    private final ReviewStatisticMapper statisticMapper;

    public StatisticService(ReviewStatisticMapper statisticMapper) {
        this.statisticMapper = statisticMapper;
    }

    /**
     * 聚合指定日期的数据（可重入：重复执行结果一致）。
     *
     * @param date 统计日
     * @return 受影响行数（0 表示当天无 DONE 记录）
     */
    public int aggregateDate(LocalDate date) {
        int rows = statisticMapper.aggregateByDate(date.toString());
        log.info("statistic aggregated date={} affectedRows={}", date, rows);
        return rows;
    }
}
