package io.github.lalala1314521.codereviewagent.review.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 审查进度发布器：审查链路的事件源 + SSE 订阅管理。
 *
 * <p>单体内存方案（多实例需换 Redis Pub/Sub）：订阅表 recordId → SSE 连接列表；
 * 回放缓存 recordId → 已发生事件，迟到连接先回放再实时——审查完成后再打开的详情页也能看到完整过程。
 * 终态后关闭所有连接（回放缓存保留）；发送失败只移除该连接；无订阅者时仅落缓存，开销可忽略。
 */
@Component
public class ReviewProgressPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReviewProgressPublisher.class);

    /** SSE 连接超时：LLM 审查长尾 30s+，留足余量 */
    private static final long EMITTER_TIMEOUT_MS = 120_000L;
    /** 心跳间隔：15s（小于常见代理空闲超时 30-60s） */
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    /** 回放缓存最多保留的 record 数（LRU 淘汰最老） */
    private static final int HISTORY_MAX_RECORDS = 200;

    /** 心跳调度器（共享单线程） */
    private final java.util.concurrent.ScheduledExecutorService heartbeatScheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /** recordId → 订阅该记录的 SSE 连接 */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /** recordId → 事件序列（回放缓存，LinkedHashMap + eldest 淘汰实现 LRU） */
    private final Map<Long, List<ProgressEvent>> history = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, List<ProgressEvent>> eldest) {
                    return size() > HISTORY_MAX_RECORDS;
                }
            });

    /**
     * 订阅某条审查记录的进度（SSE Controller 调用）。
     *
     * <p>新连接先收到回放缓存中的全部历史事件；若记录已到终态，
     * 回放完立即正常关闭（前端据此停止"进行中"动画）。
     */
    public SseEmitter subscribe(Long recordId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        subscribers.computeIfAbsent(recordId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 心跳保活：长审查（LLM 慢）无事件时，代理/网关可能按空窗超时断开连接
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(recordId, emitter), HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS,
                java.util.concurrent.TimeUnit.MILLISECONDS);

        emitter.onCompletion(() -> { heartbeat.cancel(false); unsubscribe(recordId, emitter); });
        emitter.onTimeout(() -> { heartbeat.cancel(false); unsubscribe(recordId, emitter); });
        emitter.onError(e -> { heartbeat.cancel(false); unsubscribe(recordId, emitter); });

        // 回放历史事件
        List<ProgressEvent> past = history.getOrDefault(recordId, List.of());
        boolean terminal = false;
        for (ProgressEvent event : past) {
            if (!sendTo(emitter, event)) {
                return emitter;   // 连接已断，无需继续
            }
            terminal = event.isTerminal();
        }
        if (terminal) {
            emitter.complete();
        }
        return emitter;
    }

    /**
     * 发布进度事件（审查链路各阶段调用）。
     *
     * <p>落回放缓存 → 广播给在线订阅者；终态事件后关闭并清空订阅表。
     */
    public void publish(Long recordId, String stage, String message) {
        if (recordId == null) {
            return;   // 无记录上下文（如评测链路 recordId=null），跳过
        }
        ProgressEvent event = new ProgressEvent(recordId, stage, message, System.currentTimeMillis());
        history.computeIfAbsent(recordId, k -> new CopyOnWriteArrayList<>()).add(event);
        log.debug("progress published record={} stage={} msg={}", recordId, stage, message);

        List<SseEmitter> list = subscribers.get(recordId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            if (!sendTo(emitter, event)) {
                unsubscribe(recordId, emitter);
            }
        }
        if (event.isTerminal()) {
            list.forEach(e -> {
                try {
                    e.complete();
                } catch (Exception ignored) {
                    // 已断开，忽略
                }
            });
            subscribers.remove(recordId);
        }
    }

    /** 发送注释心跳（SSE 协议注释行，前端不可见，仅维持连接）。 */
    private void sendHeartbeat(Long recordId, SseEmitter emitter) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().comment("hb"));
            }
        } catch (IOException | IllegalStateException e) {
            unsubscribe(recordId, emitter);
        }
    }

    /**
     * 发送单条事件；返回 false 表示连接已失效（调用方负责移除）。
     */
    private boolean sendTo(SseEmitter emitter, ProgressEvent event) {
        try {
            // synchronized：心跳与事件可能并发写同一连接（SseEmitter 非线程安全）
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name("stage")
                        .data(event));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("sse send failed, drop subscriber record={}: {}", event.recordId(), e.getMessage());
            return false;
        }
    }

    private void unsubscribe(Long recordId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(recordId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
