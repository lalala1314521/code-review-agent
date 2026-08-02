package io.github.lalala1314521.codereviewagent.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审查规则配置表实体（rule）。
 *
 * <p>BUILTIN：预置规则（sql_injection / no_test ...），随 schema.sql 插入，不允许删除；
 * CUSTOM：用户在管理台新建的自定义规则，可增删改。
 */
@Data
@TableName("rule")
public class RuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务规则 ID（唯一），如 no_test / sql_injection */
    private String ruleId;

    private String name;

    private String description;

    /** ERROR / WARNING / INFO */
    private String severity;

    /** 适用语言；NULL 表示全语言 */
    private String language;

    /** BUILTIN / CUSTOM */
    private String ruleType;

    /** 规则参数 JSON，如 {"maxLines": 80} */
    private String paramsJson;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
