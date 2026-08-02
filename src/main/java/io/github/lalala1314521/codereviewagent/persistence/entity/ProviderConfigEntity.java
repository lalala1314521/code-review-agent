package io.github.lalala1314521.codereviewagent.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM Provider 运行时配置（provider_config）。
 *
 * <p>apiKey 以 AES-GCM 密文存储（apiKeyEnc），apiKeyTail 仅存明文后 4 位用于掩码回显。
 * 配置合并优先级：<b>DB 覆盖 > yml 默认</b>——yml 是出厂配置，DB 是运行时管理台修改。
 */
@Data
@TableName("provider_config")
public class ProviderConfigEntity {

    /** deepseek / qianwen / openai */
    @TableId
    private String provider;

    private String baseUrl;

    /** AES-GCM 密文（Base64） */
    private String apiKeyEnc;

    /** 明文后 4 位（掩码回显用） */
    private String apiKeyTail;

    private String model;

    private Integer maxTokens;

    private BigDecimal temperature;

    private LocalDateTime updatedAt;
}
