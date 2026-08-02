package io.github.lalala1314521.codereviewagent.review.progress;

import java.util.Set;

/**
 * 审查进度事件（SSE 推送单元）。
 *
 * <p>阶段序列（对齐 UI 设计稿"Agent 思考流"）：
 * <pre>
 * RECEIVED → RULE_SCANNING → RULE_DONE → LLM_REVIEWING → LLM_DONE → DECIDED → PUBLISHING → DONE
 *                                                                                  └→ FAILED（任意阶段异常）
 * </pre>
 *
 * @param recordId review_record 主键
 * @param stage    阶段标识（见常量）
 * @param message  人类可读描述（前端直接展示）
 * @param at       事件发生时间（epoch millis）
 */
public record ProgressEvent(Long recordId, String stage, String message, long at) {

    public static final String RECEIVED = "RECEIVED";
    public static final String RULE_SCANNING = "RULE_SCANNING";
    public static final String RULE_DONE = "RULE_DONE";
    public static final String LLM_REVIEWING = "LLM_REVIEWING";
    public static final String LLM_DONE = "LLM_DONE";
    public static final String DECIDED = "DECIDED";
    public static final String PUBLISHING = "PUBLISHING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    private static final Set<String> TERMINAL_STAGES = Set.of(DONE, FAILED);

    /**
     * 是否终态（DONE/FAILED）——终态后 SSE 连接关闭，不再有新事件。
     */
    public boolean isTerminal() {
        return TERMINAL_STAGES.contains(stage);
    }
}
