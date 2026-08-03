package io.github.lalala1314521.codereviewagent.review.rule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lalala1314521.codereviewagent.model.DiffFile;
import io.github.lalala1314521.codereviewagent.model.ReviewFinding;
import io.github.lalala1314521.codereviewagent.model.UnifiedMergeRequest;
import io.github.lalala1314521.codereviewagent.persistence.entity.RuleEntity;
import io.github.lalala1314521.codereviewagent.persistence.mapper.RuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 规则引擎编排器（对齐方案设计 14.1 / 14.5）。
 *
 * <p>职责：对一次 MR 的全部 diff 文件，执行所有"已注册且已启用"的规则，合并命中结果。
 *
 * <p><b>能力-配置两级结构</b>：
 * <ul>
 *   <li>能力：Spring 容器里所有 {@link ReviewRule} 实现（代码提供）</li>
 *   <li>配置：rule 表的 enabled / params_json（DB 提供，管理台可改）</li>
 * </ul>
 * 一条规则被执行的充要条件：有实现类 且 DB 中 enabled=1。
 * 前端禁用某规则后，下一次审查立即生效——不用重启、不用改代码。
 *
 * <p>规则配置每次审查现查现用（rule 表就几十行，查询开销可忽略；
 * 若后续规则量上来可加 60s 缓存）。
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final Map<String, ReviewRule> ruleImpls;
    private final RuleMapper ruleMapper;
    private final ObjectMapper objectMapper;
    private final ContentFetcher contentFetcher;

    /**
     * Spring 自动收集所有 ReviewRule 实现注入（策略模式的装配红利）。
     */
    public RuleEngine(List<ReviewRule> rules, RuleMapper ruleMapper, ObjectMapper objectMapper,
                      ContentFetcher contentFetcher) {
        this.ruleImpls = rules.stream()
                .collect(Collectors.toMap(ReviewRule::ruleId, Function.identity()));
        this.ruleMapper = ruleMapper;
        this.objectMapper = objectMapper;
        this.contentFetcher = contentFetcher;
        log.info("rule engine initialized with {} rule impls: {}",
                ruleImpls.size(), ruleImpls.keySet());
    }

    /**
     * 对全部 diff 文件执行启用的规则。
     *
     * @return 扫描结果：规则命中的 findings + 本次生效规则的能力清单
     *         （prompt 减负与幻觉否决用）；规则引擎自身异常不影响主流程
     */
    public ScanResult scan(List<DiffFile> files, UnifiedMergeRequest mr) {
        if (files == null || files.isEmpty()) {
            return new ScanResult(List.of(), List.of(), List.of());
        }

        // 1. 加载启用中的规则配置（DB 是开关与参数的唯一权威）
        Map<String, RuleEntity> enabledRules;
        try {
            enabledRules = ruleMapper.selectList(new QueryWrapper<RuleEntity>().eq("enabled", 1))
                    .stream()
                    .collect(Collectors.toMap(RuleEntity::getRuleId, Function.identity()));
        } catch (Exception e) {
            // DB 抖动 → 规则整体跳过，LLM 兜底（规则是增强不是依赖）
            log.error("load enabled rules failed, skip rule scan: {}", e.getMessage());
            return new ScanResult(List.of(), List.of(), List.of());
        }

        List<ReviewFinding> findings = new ArrayList<>();
        for (DiffFile file : files) {
            for (Map.Entry<String, ReviewRule> entry : ruleImpls.entrySet()) {
                RuleEntity config = enabledRules.get(entry.getKey());
                if (config == null) {
                    continue;   // 未启用（或 DB 无此行）
                }
                ReviewRule rule = entry.getValue();
                if (rule.applicableLanguage() != null
                        && !rule.applicableLanguage().equals(file.language())) {
                    continue;   // 语言不匹配
                }
                try {
                    RuleContext ctx = new RuleContext(parseParams(config.getParamsJson()), files, mr, contentFetcher);
                    findings.addAll(rule.apply(file, ctx));
                } catch (Exception e) {
                    // 单条规则异常不拖垮其他规则
                    log.warn("rule {} apply failed on {}: {}", rule.ruleId(), file.newPath(), e.getMessage());
                }
            }
        }

        // 本次生效的规则能力清单（有实现类 且 启用）：prompt 减负 + 幻觉否决共用
        List<String> capableRuleIds = ruleImpls.keySet().stream()
                .filter(enabledRules::containsKey)
                .toList();
        List<String> capableSummaries = capableRuleIds.stream()
                .map(id -> enabledRules.get(id).getName() + "（" + id + "）")
                .toList();

        if (!findings.isEmpty()) {
            log.info("rule scan done project={} mr={} hits={} rules={}",
                    mr.projectId(), mr.mrIid(), findings.size(),
                    findings.stream().map(ReviewFinding::ruleId).distinct().toList());
        }
        return new ScanResult(findings, capableRuleIds, capableSummaries);
    }

    /**
     * 规则扫描结果。
     *
     * @param findings         规则命中（source=RULE，confidence=1.0）
     * @param capableRuleIds   本次生效规则的 ruleId 集合——幻觉否决依据
     *                         （LLM 报这些 ruleId 但规则同文件未命中 → 视为幻觉丢弃）
     * @param capableSummaries 生效规则能力清单（"规则名（ruleId）"），prompt 告知 LLM 不必再扫
     */
    public record ScanResult(List<ReviewFinding> findings, List<String> capableRuleIds, List<String> capableSummaries) {}

    /**
     * 当前启用的规则数（进度推送用）；查询失败返回 0。
     */
    public int countEnabledRules() {
        try {
            Long count = ruleMapper.selectCount(new QueryWrapper<RuleEntity>().eq("enabled", 1));
            return count == null ? 0 : count.intValue();
        } catch (Exception e) {
            log.warn("count enabled rules failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * params_json → Map；空/非法 JSON 返回空 Map（规则实现用默认值）。
     */
    private Map<String, String> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(paramsJson, new TypeReference<>() {});
            Map<String, String> result = new HashMap<>();
            raw.forEach((k, v) -> result.put(k, String.valueOf(v)));
            return result;
        } catch (Exception e) {
            log.warn("invalid rule params_json, use defaults: {}", paramsJson);
            return Collections.emptyMap();
        }
    }
}
