package io.github.lalala1314521.codereviewagent.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审查发现表实体（review_finding）。
 *
 * <p>一条 review_record 对应 N 条 finding（1:N），
 * 外键级联删除（ON DELETE CASCADE）。
 */
@Data
@TableName("review_finding")
public class ReviewFindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 外键 → review_record.id */
    private Long reviewRecordId;

    private String filePath;

    /** NULL 表示文件级问题 */
    private Integer lineNumber;

    /** ERROR / WARNING / INFO */
    private String severity;

    /** 规则 ID 或 "llm_*" */
    private String ruleId;

    private String message;

    private String suggestion;

    /** RULE / LLM */
    private String source;

    /** 0-100（DB 存百分制；内存模型 ReviewFinding 是 0-1，转换在 Service 层） */
    private BigDecimal confidence;

    private LocalDateTime createdAt;
}
