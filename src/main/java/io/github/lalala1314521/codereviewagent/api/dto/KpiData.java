package io.github.lalala1314521.codereviewagent.api.dto;

/**
 * 仪表盘 KPI 数据（对齐方案设计 12.2，字段名与前端 KpiCard 一致）。
 *
 * @param todayReviewCount     今日审查数
 * @param yesterdayReviewCount 昨日审查数（算环比）
 * @param avgDurationMs        今日平均耗时
 * @param prevAvgDurationMs    昨日平均耗时
 * @param passRate             今日通过率（APPROVE / DONE）
 * @param prevPassRate         昨日通过率
 * @param blockedMrCount       今日被 BLOCK 的 MR 数
 */
public record KpiData(
        long todayReviewCount,
        long yesterdayReviewCount,
        long avgDurationMs,
        long prevAvgDurationMs,
        double passRate,
        double prevPassRate,
        long blockedMrCount
) {}
