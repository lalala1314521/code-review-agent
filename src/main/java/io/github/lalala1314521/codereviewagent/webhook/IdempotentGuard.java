package io.github.lalala1314521.codereviewagent.webhook;

import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 幂等守卫：防止同一 MR 同一 commit 被重复审查。
 *
 * <p>核心机制：Redis {@code SETNX} + TTL。键：{@code platform:projectId:mrIid:commitSha}。
 * TTL 1 小时覆盖 webhook 超时/网络抖动的多次重试；审查完成后不删 key 防重试再触发。
 * DB 唯一索引（review_record.uk_commit）在 V2 持久化层作第二道保险。
 * SETNX 原子性保证多实例并发下只有一个实例拿到锁。
 */
@Component
public class IdempotentGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotentGuard.class);
    private static final String KEY_PREFIX = "codereview:idem:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    public IdempotentGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取幂等锁。
     *
     * @param mr 统一 MR 模型
     * @return true 拿到锁（首次处理）；false 已存在（重复请求，应跳过）
     */
    public boolean tryAcquire(UnifiedMergeRequest mr) {
        String key = buildKey(mr);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", TTL);

        boolean firstTime = Boolean.TRUE.equals(acquired);
        if (firstTime) {
            log.info("idempotent acquired key={}", key);
        } else {
            log.warn("idempotent hit key={}, skip duplicate review", key);
        }
        return firstTime;
    }

    /**
     * 构建幂等键。
     *
     * <p>键包含 platform + projectId + mrIid + commitSha，确保：
     * <ul>
     *   <li>同一 MR 不同 commit → 不同键（新 push 触发新审查）</li>
     *   <li>同一 MR 同一 commit → 相同键（重复 webhook 被拦截）</li>
     * </ul>
     */
    private String buildKey(UnifiedMergeRequest mr) {
        return KEY_PREFIX
                + mr.platform() + ":"
                + mr.projectId() + ":"
                + mr.mrIid() + ":"
                + mr.commitSha();
    }
}
