package io.github.lalala1314521.codereviewagent.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.lalala1314521.codereviewagent.common.api.ApiResponse;
import io.github.lalala1314521.codereviewagent.common.exception.BizException;
import io.github.lalala1314521.codereviewagent.persistence.entity.RuleEntity;
import io.github.lalala1314521.codereviewagent.persistence.mapper.RuleMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审查规则 CRUD API（方案设计 12.5）。
 *
 * <p>BUILTIN 规则随 schema.sql 预置，<b>不允许删除</b>（审查 prompt 与规则 ID
 * 有对应关系，删了 LLM 输出里的 ruleId 就成了野指针）；CUSTOM 规则用户自建，可增删改。
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleMapper ruleMapper;

    public RuleController(RuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /**
     * 规则列表（支持 language / enabled 筛选，默认按严重度排序）。
     */
    @GetMapping
    public ApiResponse<List<RuleEntity>> list(@RequestParam(required = false) String language,
                                              @RequestParam(required = false) Boolean enabled) {
        List<RuleEntity> rules = ruleMapper.selectList(new QueryWrapper<RuleEntity>()
                .eq(StringUtils.hasText(language), "language", language)
                .eq(enabled != null, "enabled", enabled)
                .orderByAsc("FIELD(severity, 'ERROR', 'WARNING', 'INFO')")
                .orderByAsc("id"));
        return ApiResponse.ok(rules);
    }

    /**
     * 新建自定义规则（ruleId 全局唯一，类型固定 CUSTOM）。
     */
    @PostMapping
    public ApiResponse<RuleEntity> create(@Valid @RequestBody RuleRequest req) {
        Long exists = ruleMapper.selectCount(new QueryWrapper<RuleEntity>().eq("rule_id", req.ruleId()));
        if (exists != null && exists > 0) {
            throw new BizException(400, "ruleId 已存在: " + req.ruleId());
        }
        RuleEntity entity = new RuleEntity();
        applyRequest(entity, req);
        entity.setRuleType("CUSTOM");
        entity.setEnabled(true);
        ruleMapper.insert(entity);
        return ApiResponse.ok(entity);
    }

    /**
     * 更新规则（BUILTIN 只允许改 enabled / paramsJson，保护预置语义）。
     */
    @PutMapping("/{id}")
    public ApiResponse<RuleEntity> update(@PathVariable Long id, @Valid @RequestBody RuleRequest req) {
        RuleEntity entity = mustGet(id);
        if ("BUILTIN".equals(entity.getRuleType())) {
            // BUILTIN 规则只放开开关和参数，名称/严重度等语义字段不可改
            entity.setParamsJson(req.paramsJson());
        } else {
            applyRequest(entity, req);
        }
        ruleMapper.updateById(entity);
        return ApiResponse.ok(entity);
    }

    /**
     * 删除规则（BUILTIN 拒绝）。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        RuleEntity entity = mustGet(id);
        if ("BUILTIN".equals(entity.getRuleType())) {
            throw new BizException(400, "内置规则不允许删除，可禁用");
        }
        ruleMapper.deleteById(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<RuleEntity> enable(@PathVariable Long id) {
        return setEnabled(id, true);
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<RuleEntity> disable(@PathVariable Long id) {
        return setEnabled(id, false);
    }

    private ApiResponse<RuleEntity> setEnabled(Long id, boolean enabled) {
        RuleEntity entity = mustGet(id);
        entity.setEnabled(enabled);
        ruleMapper.updateById(entity);
        return ApiResponse.ok(entity);
    }

    private RuleEntity mustGet(Long id) {
        RuleEntity entity = ruleMapper.selectById(id);
        if (entity == null) {
            throw new BizException(404, "rule not found: " + id);
        }
        return entity;
    }

    private void applyRequest(RuleEntity entity, RuleRequest req) {
        entity.setRuleId(req.ruleId());
        entity.setName(req.name());
        entity.setDescription(req.description());
        entity.setSeverity(req.severity());
        entity.setLanguage(req.language());
        entity.setParamsJson(req.paramsJson());
    }

    /**
     * 规则新建/更新请求体（带参数校验）。
     */
    public record RuleRequest(
            @NotBlank(message = "ruleId 不能为空") String ruleId,
            @NotBlank(message = "name 不能为空") String name,
            String description,
            @Pattern(regexp = "ERROR|WARNING|INFO", message = "severity 只能是 ERROR/WARNING/INFO")
            @NotBlank(message = "severity 不能为空") String severity,
            String language,
            String paramsJson
    ) {}
}
