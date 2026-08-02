package io.github.lalala1314521.codereviewagent.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 按日聚合统计表实体（review_statistic）。
 *
 * <p>由定时任务从 review_record 聚合写入，仪表盘/历史图表直接查它，
 * 避免每次全表扫描。uk_date_repo 唯一索引保证幂等重跑。
 */
@Data
@TableName("review_statistic")
public class ReviewStatisticEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private String platform;

    private String repoPath;

    private Integer totalCount;

    private Integer approveCount;

    private Integer needsFixCount;

    private Integer blockCount;

    private Long avgDurationMs;
}
