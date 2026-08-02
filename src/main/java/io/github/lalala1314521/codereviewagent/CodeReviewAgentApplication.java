package io.github.lalala1314521.codereviewagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Code Review Agent 启动类。
 *
 * <p>MVP 阶段：接 GitLab Webhook → 拉 MR diff → 调 DeepSeek → 回写 MR 评论。
 * V1：Redis 幂等、异步线程池、Verdict 裁决层、LLM Provider 抽象。
 * V2：MySQL 持久化 + Management API（本类通过 @MapperScan 扫描 Mapper 接口）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("io.github.lalala1314521.codereviewagent.persistence.mapper")
public class CodeReviewAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeReviewAgentApplication.class, args);
    }
}
