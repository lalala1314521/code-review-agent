package io.github.lalala1314521.codereviewagent.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.lalala1314521.codereviewagent.persistence.entity.ReviewStatisticEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * review_statistic Mapper。
 *
 * <p>核心方法 {@link #aggregateByDate}：一条 SQL 同时完成"聚合 + 幂等 upsert"——
 * INSERT...SELECT 按日 GROUP BY review_record，撞 uk_date_repo 唯一键则 UPDATE。
 * 定时任务失败重跑、手动补数反复执行，结果都正确（幂等是补数能力的前提）。
 */
@Mapper
public interface ReviewStatisticMapper extends BaseMapper<ReviewStatisticEntity> {

    /**
     * 按日聚合 review_record 写入预聚合表（仅统计 DONE 记录）。
     *
     * @param date 统计日（yyyy-MM-dd）；聚合 [date 00:00, date+1 00:00) 区间
     * @return 受影响行数（INSERT 新增 1 行/repo；UPDATE 覆盖已有行）
     */
    @Insert("""
            INSERT INTO review_statistic
                (stat_date, platform, repo_path, total_count, approve_count, needs_fix_count, block_count, avg_duration_ms)
            SELECT
                #{date}, platform, repo_path,
                COUNT(*),
                COALESCE(SUM(CASE WHEN conclusion = 'APPROVE' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN conclusion = 'NEEDS_FIX' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN conclusion = 'BLOCK' THEN 1 ELSE 0 END), 0),
                COALESCE(AVG(duration_ms), 0)
            FROM review_record
            WHERE triggered_at >= #{date}
              AND triggered_at < #{date} + INTERVAL 1 DAY
              AND status = 'DONE'
            GROUP BY platform, repo_path
            ON DUPLICATE KEY UPDATE
                total_count = VALUES(total_count),
                approve_count = VALUES(approve_count),
                needs_fix_count = VALUES(needs_fix_count),
                block_count = VALUES(block_count),
                avg_duration_ms = VALUES(avg_duration_ms)
            """)
    int aggregateByDate(@Param("date") String date);
}
