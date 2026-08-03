package io.github.lalala1314521.codereviewagent.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * push 去抖存储：连续 push 合并为一次审查。
 *
 * <p>webhook 归一后的 mr 以 JSON 存 Redis hash（field=platform:projectId:mrIid，
 * 覆盖式——后到的 push 覆盖先到的），并记录更新时间。
 * {@link DebounceScheduler} 定期扫描：静默超过窗口期（20s 没有新 push）才触发审查。
 * 连续 push 场景下只有最后一个 commit 会被审，前面的被合并（省 N-1 次 LLM 调用）。
 *
 * <p>Redis 不可用时 put 抛异常，由调用方降级为直接触发（功能不退化）。
 */
@Component
public class DebounceStore {

    private static final Logger log = LoggerFactory.getLogger(DebounceStore.class);
    private static final String HASH_KEY = "codereview:debounce";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DebounceStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 存入（覆盖式）待审 mr。 */
    public void put(UnifiedMergeRequest mr) {
        try {
            String json = objectMapper.writeValueAsString(mr);
            redisTemplate.opsForHash().put(HASH_KEY, field(mr), System.currentTimeMillis() + "|" + json);
            log.info("debounce queued platform={} project={} mr={} sha={}",
                    mr.platform(), mr.projectId(), mr.mrIid(), mr.commitSha());
        } catch (Exception e) {
            throw new IllegalStateException("debounce store unavailable: " + e.getMessage(), e);
        }
    }

    /** 取出所有静默超过 quietMs 的条目（不删除，由调用方触发成功后删）。 */
    public List<PendingEntry> pollQuiet(long quietMs) {
        Map<Object, Object> all;
        try {
            all = redisTemplate.opsForHash().entries(HASH_KEY);
        } catch (Exception e) {
            log.error("debounce scan failed: {}", e.getMessage());
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<PendingEntry> ready = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : all.entrySet()) {
            try {
                String value = String.valueOf(entry.getValue());
                long updatedAt = Long.parseLong(value.substring(0, value.indexOf('|')));
                if (now - updatedAt < quietMs) {
                    continue;   // 窗口内还在来 push，继续等
                }
                UnifiedMergeRequest mr = objectMapper.readValue(
                        value.substring(value.indexOf('|') + 1), UnifiedMergeRequest.class);
                ready.add(new PendingEntry(String.valueOf(entry.getKey()), mr));
            } catch (Exception e) {
                log.warn("debounce entry corrupt, drop: {}", entry.getKey());
                remove(String.valueOf(entry.getKey()));
            }
        }
        return ready;
    }

    /** 删除指定条目（触发后调用）。 */
    public void remove(String field) {
        try {
            redisTemplate.opsForHash().delete(HASH_KEY, field);
        } catch (Exception e) {
            log.error("debounce remove failed: {}", e.getMessage());
        }
    }

    private String field(UnifiedMergeRequest mr) {
        return mr.platform() + ":" + mr.projectId() + ":" + mr.mrIid();
    }

    /** 待触发条目。 */
    public record PendingEntry(String field, UnifiedMergeRequest mr) {}
}
