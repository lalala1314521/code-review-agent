package io.github.lalala1314521.codereviewagent.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.Severity;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.model.Verdict;
import io.github.lalala1314521.codereviewagent.persistence.ReviewRecordService;
import io.github.lalala1314521.codereviewagent.platform.PlatformRouter;
import io.github.lalala1314521.codereviewagent.review.diff.DiffParser;
import io.github.lalala1314521.codereviewagent.review.llm.ActiveProviderService;
import io.github.lalala1314521.codereviewagent.review.llm.LlmClient;
import io.github.lalala1314521.codereviewagent.review.progress.ProgressEvent;
import io.github.lalala1314521.codereviewagent.review.progress.ReviewProgressPublisher;
import io.github.lalala1314521.codereviewagent.review.rule.RuleEngine;
import io.github.lalala1314521.codereviewagent.verdict.VerdictDecider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 审查引擎：编排"拉 diff → 构造 prompt → 调 LLM → 解析 findings → 裁决"完整流程。
 *
 * <p>MVP：同步处理，返回 Markdown。V1：{@link #reviewAsync} 异步，返回结构化 Verdict。
 */
@Component
public class ReviewEngine {

    private static final Logger log = LoggerFactory.getLogger(ReviewEngine.class);
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*\\]", Pattern.DOTALL);

    private final PlatformRouter platformRouter;
    private final ActiveProviderService activeProviderService;
    private final PromptBuilder promptBuilder;
    private final VerdictDecider verdictDecider;
    private final ObjectMapper objectMapper;
    private final ReviewRecordService reviewRecordService;
    private final DiffParser diffParser;
    private final RuleEngine ruleEngine;
    private final ReviewProgressPublisher progressPublisher;

    public ReviewEngine(PlatformRouter platformRouter, ActiveProviderService activeProviderService, PromptBuilder promptBuilder,
                        VerdictDecider verdictDecider, ObjectMapper objectMapper,
                        ReviewRecordService reviewRecordService, DiffParser diffParser, RuleEngine ruleEngine,
                        ReviewProgressPublisher progressPublisher) {
        this.platformRouter = platformRouter;
        this.activeProviderService = activeProviderService;
        this.promptBuilder = promptBuilder;
        this.verdictDecider = verdictDecider;
        this.objectMapper = objectMapper;
        this.reviewRecordService = reviewRecordService;
        this.diffParser = diffParser;
        this.ruleEngine = ruleEngine;
        this.progressPublisher = progressPublisher;
    }

    /**
     * 同步审查（MVP 保留，用于评测）。
     *
     * @return Markdown 审查文本
     */
    public String review(UnifiedMergeRequest mr) {
        Verdict verdict = reviewStructured(mr);
        return verdict.summary();
    }

    /**
     * 结构化审查（V1 新增，返回 Verdict；V2 起内部走 {@link #reviewWithDiff}）。
     *
     * <p>流程：拉 diff → {@link #reviewWithDiff}（规则扫描 → LLM → 合并去重 → 裁决）。
     */
    public Verdict reviewStructured(UnifiedMergeRequest mr) {
        return reviewStructured(mr, null);
    }

    /**
     * 结构化审查（带进度推送）。
     *
     * @param recordId review_record 主键；非 null 时各阶段向 SSE 订阅者推送进度
     */
    public Verdict reviewStructured(UnifiedMergeRequest mr, Long recordId) {
        log.info("review start platform={} project={} mr={} commit={}", mr.platform(), mr.projectId(), mr.mrIid(), mr.commitSha());

        // 1. 拉 diff（平台路由：GITLAB / GITHUB）
        String diff = platformRouter.getDiff(mr);
        if (diff.isBlank()) {
            log.warn("empty diff, skip review platform={} project={} mr={}", mr.platform(), mr.projectId(), mr.mrIid());
            return verdictDecider.decide(List.of());
        }

        // 2. 填充 diff（record 不可变，重新构造）
        UnifiedMergeRequest mrWithDiff = new UnifiedMergeRequest(
                mr.platform(), mr.projectId(), mr.repoPath(), mr.mrIid(),
                mr.commitSha(), mr.sourceBranch(), mr.targetBranch(),
                mr.title(), mr.description(), mr.authorName(), mr.authorUsername(),
                mr.webUrl(), diff
        );
        return reviewWithDiff(mrWithDiff, null, recordId);
    }

    /**
     * 全链路审查（生产入口）：diff 已填充，文件路径以 diff 文件头为准。
     */
    public Verdict reviewWithDiff(UnifiedMergeRequest mr) {
        return reviewWithDiff(mr, null);
    }

    /**
     * 全链路审查：diff 已填充，可指定裸 hunk 兜底路径。
     */
    public Verdict reviewWithDiff(UnifiedMergeRequest mr, String defaultFilePath) {
        return reviewWithDiff(mr, defaultFilePath, null);
    }

    /**
     * 全链路审查（V2）：diff 已填充时的核心流程。
     *
     * <p><b>规则与 LLM 协同</b>：
     * <ol>
     *   <li>规则先跑：确定性模式（SQL 拼接、硬编码密钥、空 catch...），命中即真理 confidence=1.0</li>
     *   <li>规则发现写入 system prompt，要求 LLM 不重复报告</li>
     *   <li>LLM 后跑：语义判断 + 规则漏网的模式问题</li>
     *   <li>合并去重：同文件、行号差 ≤2、同 ruleId 视为重复，保留 RULE</li>
     * </ol>
     *
     * <p>与 {@link #reviewStructured} 差别仅在 diff 来源——本方法由调用方填好
     * （评测链路直接喂用例 diff，复用同一套真实行为）。
     *
     * @param defaultFilePath 裸 hunk（无文件头的 diff）的兜底文件路径；生产传 null
     * @param recordId        review_record 主键；非 null 时推送 SSE 进度（评测传 null 跳过）
     */
    public Verdict reviewWithDiff(UnifiedMergeRequest mr, String defaultFilePath, Long recordId) {
        // 1. diff 结构化 + 规则引擎先跑
        progressPublisher.publish(recordId, ProgressEvent.RULE_SCANNING,
                "正在匹配审查规则集（" + ruleEngine.countEnabledRules() + " 条规则）");
        List<DiffFile> diffFiles = diffParser.parse(mr.diff(), defaultFilePath);
        RuleEngine.ScanResult scanResult = ruleEngine.scan(diffFiles, mr);
        List<ReviewFinding> ruleFindings = scanResult.findings();
        progressPublisher.publish(recordId, ProgressEvent.RULE_DONE,
                "规则扫描完成，命中 " + ruleFindings.size() + " 个问题");

        // 2. 构造 prompt（规则能力清单减负 + 本次命中不重复报）
        String systemPrompt = buildStructuredSystemPrompt(ruleFindings, scanResult.capableSummaries());
        String userPrompt = promptBuilder.buildUserPrompt(mr);

        // 3. 调 LLM：任务开始时固定当前活动 Provider
        LlmClient llmClient = activeProviderService.getActiveClient();
        progressPublisher.publish(recordId, ProgressEvent.LLM_REVIEWING,
                "正在调用 " + llmClient.providerName() + " 深度审查");
        String rawResponse = llmClient.chat(systemPrompt, userPrompt);

        // 4. 解析 JSON findings（容错）
        List<ReviewFinding> llmFindings = parseFindings(rawResponse, mr.repoPath());
        progressPublisher.publish(recordId, ProgressEvent.LLM_DONE,
                "LLM 审查完成，发现 " + llmFindings.size() + " 个问题");

        // 5. 合并去重（RULE 优先）+ 幻觉否决
        List<ReviewFinding> merged = mergeFindings(ruleFindings, llmFindings, scanResult.capableRuleIds());

        // 6. Verdict 裁决
        Verdict verdict = verdictDecider.decide(merged);
        progressPublisher.publish(recordId, ProgressEvent.DECIDED,
                "裁决完成：" + conclusionText(verdict)
                        + String.format("（置信度 %.0f%%）", verdict.confidence() * 100));
        log.info("review done project={} mr={} conclusion={} confidence={} ruleHits={} llmHits={} merged={}",
                mr.projectId(), mr.mrIid(), verdict.conclusion(), verdict.confidence(),
                ruleFindings.size(), llmFindings.size(), merged.size());
        return verdict;
    }

    /**
     * 结论文案（SSE 进度展示用）。
     */
    private String conclusionText(Verdict verdict) {
        return switch (verdict.conclusion()) {
            case APPROVE -> "建议合并";
            case NEEDS_FIX -> "需修复";
            case BLOCK -> "阻塞";
        };
    }

    /**
     * 合并规则与 LLM 的发现并去重。
     *
     * <p>去重键：同 filePath + 行号差 ≤2 + 同 ruleId，重复保留 RULE 侧（确定性 > LLM）。
     *
     * <p><b>幻觉否决</b>：LLM 报的 ruleId 在规则能力范围内，但规则同文件未命中——规则是确定性的，
     * "扫过且说没有"而 LLM 说有 = 幻觉，直接丢弃（case_004 实证：LLM 报 todo_comment，
     * diff 里根本没有 TODO）。LLM 自造 ruleId（如 undefined_type）的语义问题不受影响。
     */
    private List<ReviewFinding> mergeFindings(List<ReviewFinding> ruleFindings, List<ReviewFinding> llmFindings,
                                              List<String> capableRuleIds) {
        List<ReviewFinding> merged = new ArrayList<>(ruleFindings);
        for (ReviewFinding llmFinding : llmFindings) {
            boolean duplicate = ruleFindings.stream().anyMatch(ruleFinding ->
                    java.util.Objects.equals(ruleFinding.filePath(), llmFinding.filePath())
                            && java.util.Objects.equals(ruleFinding.ruleId(), llmFinding.ruleId())
                            && lineClose(ruleFinding.lineNumber(), llmFinding.lineNumber()));
            if (duplicate) {
                log.debug("dedup llm finding covered by rule: {} @ {}:{}",
                        llmFinding.ruleId(), llmFinding.filePath(), llmFinding.lineNumber());
                continue;
            }
            // 幻觉否决：规则能力范围内以规则判定为准
            if (capableRuleIds.contains(llmFinding.ruleId())) {
                boolean ruleHitSameFileSameRule = ruleFindings.stream().anyMatch(ruleFinding ->
                        java.util.Objects.equals(ruleFinding.filePath(), llmFinding.filePath())
                                && java.util.Objects.equals(ruleFinding.ruleId(), llmFinding.ruleId()));
                if (!ruleHitSameFileSameRule) {
                    log.info("hallucination vetoed: llm reported rule-capable {} @ {}:{} but rule scan found nothing",
                            llmFinding.ruleId(), llmFinding.filePath(), llmFinding.lineNumber());
                    continue;
                }
            }
            merged.add(llmFinding);
        }
        return merged;
    }

    /**
     * 行号接近判定：都为空算接近（文件级）；一方为空不算；否则差 ≤2。
     */
    private boolean lineClose(Integer a, Integer b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a - b) <= 2;
    }

    /**
     * 异步审查（V1 新增，V2 接入落库）。
     *
     * <p>Controller 调用后立即返回，不阻塞 webhook ACK，后台线程池执行。
     * 落库失败仅告警，不影响审查结果与评论回写。
     *
     * @param mr       从 webhook payload 归一来的统一 MR
     * @param recordId review_record 主键；为 null 表示 PENDING 记录创建失败，跳过落库
     * @return CompletableFuture<Verdict>，调用方无需等待
     */
    @Async("reviewTaskExecutor")
    public CompletableFuture<Verdict> reviewAsync(UnifiedMergeRequest mr, Long recordId) {
        log.info("async review start project={} mr={} commit={} recordId={} thread={}",
                mr.projectId(), mr.mrIid(), mr.commitSha(), recordId, Thread.currentThread().getName());
        return executeAsync(recordId, () -> reviewStructured(mr, recordId));
    }

    /**
     * 异步审查（diff 已填充，不走平台拉取）——demo / 联调链路用。
     *
     * <p>与 {@link #reviewAsync} 唯一差别是 diff 来源：webhook 链路从平台拉，
     * 本方法由调用方直接填好（{@link DemoController} 场景无真实平台可连）。
     */
    @Async("reviewTaskExecutor")
    public CompletableFuture<Verdict> reviewWithDiffAsync(UnifiedMergeRequest mr, Long recordId) {
        log.info("async review(with-diff) start recordId={} thread={}", recordId, Thread.currentThread().getName());
        return executeAsync(recordId, () -> reviewWithDiff(mr, null, recordId));
    }

    /**
     * 异步编排公共骨架：REVIEWING → 执行审查 → DONE（含 findings 落库）。
     * 落库失败仅告警，不影响审查结果。
     */
    private CompletableFuture<Verdict> executeAsync(Long recordId, java.util.function.Supplier<Verdict> reviewAction) {
        long startNanos = System.nanoTime();

        if (recordId != null) {
            try {
                reviewRecordService.markReviewing(recordId);
            } catch (Exception e) {
                log.warn("mark reviewing failed, continue review recordId={}: {}", recordId, e.getMessage());
            }
        }

        Verdict result = reviewAction.get();

        if (recordId != null) {
            try {
                reviewRecordService.completeRecord(recordId, result, elapsedMs(startNanos));
            } catch (Exception e) {
                log.error("persist review result failed recordId={}: {}", recordId, e.getMessage(), e);
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 审查失败落库 + 进度终态（由 webhook Controller 的 exceptionally 调用）。
     *
     * <p>单独提取是因为 @Async 方法内抛异常后无法在本类自调用捕获。
     */
    public void markFailed(Long recordId, long durationMs) {
        markFailed(recordId, durationMs, null);
    }

    /**
     * 审查失败落库 + 进度终态（带失败原因）。
     *
     * @param reason 失败原因（如 LLM 返回的 4xx 消息）；null 用默认文案
     */
    public void markFailed(Long recordId, long durationMs, String reason) {
        if (recordId == null) {
            return;
        }
        String message = reason != null && !reason.isBlank()
                ? "审查失败：" + reason
                : "审查失败，请查看服务日志";
        progressPublisher.publish(recordId, ProgressEvent.FAILED, message);
        try {
            reviewRecordService.failRecord(recordId, durationMs);
        } catch (Exception e) {
            log.error("persist fail status failed recordId={}: {}", recordId, e.getMessage(), e);
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 构造要求 JSON 输出的 system prompt。
     *
     * <p>V2 起追加"规则已发现"段落：把规则引擎命中结果列给 LLM，
     * 要求不重复报告——避免同一问题在最终结果里出现两条。
     */
    private String buildStructuredSystemPrompt(List<ReviewFinding> ruleFindings, List<String> capableSummaries) {
        String base = """
                你是一名资深代码审查员。请审查下面的代码变更，输出 JSON 数组格式的审查结果。

                ## 输出格式（严格遵守）

                ```json
                [
                  {
                    "filePath": "src/main/java/com/demo/dao/UserDao.java",
                    "lineNumber": 45,
                    "severity": "ERROR",
                    "ruleId": "sql_injection",
                    "message": "SQL 拼接存在注入风险",
                    "suggestion": "使用 #{} 参数化绑定替代字符串拼接",
                    "confidence": 0.95
                  }
                ]
                ```

                若无问题，输出 []。

                ## Severity 分级标准（严格遵守）

                - **ERROR**：确定的安全漏洞或数据风险。如：SQL 注入、硬编码密钥、明确空指针、catch 完全为空
                - **WARNING**：确定的工程问题，不影响安全但影响质量。如：新增业务类无对应测试、明确批量操作无 @Transactional
                - **INFO**：建议性改进。如：TODO/FIXME、魔法数字、命名风格

                **关键约束**：
                - TODO/FIXME 只能是 INFO，不能是 WARNING 或 ERROR
                - "可能""疑似""潜在"的问题不要报，只报代码中**明确看到**的问题
                - 不要推测代码意图，只基于 diff 中可见的代码判断

                ## 审查重点（规则未覆盖的语义问题）

                1. **空指针**：代码中明确看到的解引用（不是"可能为 null"的推测）
                2. **编译级错误**：未定义变量、未导入类型、方法签名不匹配
                3. **事务边界**：多次写操作无 @Transactional 的确认（规则已圈定嫌疑，你做语义确认）
                4. **业务逻辑**：条件错误、边界遗漏、并发问题、逻辑矛盾

                ## 禁止报告的情况

                - "可能引发空指针"（但代码中没看到明确解引用）
                - "建议添加空值检查"（除非代码中明确看到 null 解引用）
                - "建议处理异常"（除非 catch 块完全为空）
                - "建议添加事务"（除非代码中明确看到批量操作）
                - 对第三方库、框架行为的推测性报告
                - **"未定义的变量/类型"（针对类成员）**：diff 只展示变更片段，类的其他字段、
                  父类成员、依赖注入的组件（如 thirdParty、jdbc、mapper、log）不会在 diff 中出现，
                  它们的存在是合理的——只有同一方法内的局部变量明显未定义、或明显拼写错误才可报告
                - **编译错误类推断（类型不匹配、方法不存在、签名不符）**：涉及 diff 片段之外的符号
                  （如 jdbc 的泛型方法、第三方类的返回类型）时，你无法证明它编译不过——
                  只有在同一 diff 片段内能直接证明的编译错误才可报告（如调用了一个刚刚被删除的
                  同文件方法），否则视为推测，不要报告

                ## 约束

                - severity 只能是 ERROR / WARNING / INFO，严格按上述分级
                - lineNumber 必须基于 diff 中的行号推断（不是文件绝对行号）
                - confidence 是 0.0~1.0 的浮点数，表示你对这条发现的把握程度
                - 不要复述代码，只输出 JSON
                - 中文回答 message 和 suggestion
                - 宁可漏报不可误报，不确定的问题不要报
                """;

        StringBuilder sb = new StringBuilder(base);

        // token 减负：规则已覆盖的问题从"审查重点"移除
        if (capableSummaries != null && !capableSummaries.isEmpty()) {
            sb.append("\n\n## 规则引擎已覆盖（以下内容不必再扫描）\n\n");
            sb.append("以下模式类问题已由确定性规则引擎完成扫描，其结果直接计入审查结论。");
            sb.append("请把全部注意力放在上面的语义问题上，不要再报告这些类型的问题：\n\n");
            for (String summary : capableSummaries) {
                sb.append("- ").append(summary).append('\n');
            }
        }

        // 本次命中：要求 LLM 不重复报告
        if (ruleFindings != null && !ruleFindings.isEmpty()) {
            sb.append("\n\n## 规则引擎本次已发现的问题（不要重复报告）\n\n");
            sb.append("以下问题已被确定性规则识别，将直接计入审查结果：\n\n");
            for (ReviewFinding f : ruleFindings) {
                sb.append(String.format("- [%s] %s:%s (%s) %s\n",
                        f.severity(), f.filePath(),
                        f.lineNumber() == null ? "文件级" : f.lineNumber(),
                        f.ruleId(), f.message()));
            }
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 输出的 JSON findings（容错：直接解析 → 正则提取 → 降级为单条 INFO）。
     */
    private List<ReviewFinding> parseFindings(String rawResponse, String repoPath) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }

        // 1. 去掉 markdown 包裹
        String cleaned = rawResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        // 2. 尝试直接解析
        try {
            List<LlmFindingDto> dtos = objectMapper.readValue(
                    cleaned,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LlmFindingDto.class)
            );
            return dtos.stream()
                    .map(dto -> toReviewFinding(dto, repoPath))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("direct json parse failed, try regex extract: {}", e.getMessage());
        }

        // 3. 正则提取 [...] 片段
        Matcher m = JSON_ARRAY_PATTERN.matcher(cleaned);
        if (m.find()) {
            try {
                List<LlmFindingDto> dtos = objectMapper.readValue(
                        m.group(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, LlmFindingDto.class)
                );
                return dtos.stream()
                        .map(dto -> toReviewFinding(dto, repoPath))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("regex json parse failed: {}", e.getMessage());
            }
        }

        // 4. 降级：整段响应包装成单条 INFO 级 finding
        log.error("llm response parse failed, fallback to summary finding, raw={}",
                rawResponse.substring(0, Math.min(200, rawResponse.length())));
        return List.of(new ReviewFinding(
                null, null, Severity.INFO, "llm_summary",
                "LLM 输出格式异常，请人工查看原始响应",
                rawResponse.substring(0, Math.min(500, rawResponse.length())),
                "LLM", 0.3
        ));
    }

    /**
     * LLM 输出 DTO → ReviewFinding 转换。
     */
    private ReviewFinding toReviewFinding(LlmFindingDto dto, String repoPath) {
        // 行号越界检查
        Integer lineNumber = dto.lineNumber();
        if (lineNumber != null && lineNumber <= 0) {
            lineNumber = null;
        }

        // severity 解析（容错）
        Severity severity;
        try {
            severity = Severity.valueOf(dto.severity() != null ? dto.severity() : "INFO");
        } catch (IllegalArgumentException e) {
            severity = Severity.INFO;
        }

        // confidence 边界检查
        Double confidence = dto.confidence();
        if (confidence == null || confidence < 0 || confidence > 1) {
            confidence = 0.7;  // 默认中等置信度
        }

        return new ReviewFinding(
                dto.filePath(),
                lineNumber,
                severity,
                dto.ruleId() != null ? dto.ruleId() : "llm_general",
                dto.message() != null ? dto.message() : "无描述",
                dto.suggestion(),
                "LLM",
                confidence
        );
    }

    /**
     * LLM 输出 DTO（内部使用）。
     */
    private record LlmFindingDto(
            String filePath,
            Integer lineNumber,
            String severity,
            String ruleId,
            String message,
            String suggestion,
            Double confidence
    ) {}
}


