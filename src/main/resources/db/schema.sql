-- ===== Code Review Agent 数据库结构（MySQL 8）=====
-- 对齐方案设计文档第 11 节；字符集 utf8mb4，引擎 InnoDB
-- 使用方式：mysql -u root -p code_review_agent < schema.sql

CREATE DATABASE IF NOT EXISTS code_review_agent DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE code_review_agent;

-- ===== 11.1 review_record（审查记录主表）=====
CREATE TABLE IF NOT EXISTS review_record (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    trace_id        VARCHAR(64)  NOT NULL COMMENT '链路追踪 ID',
    platform        VARCHAR(16)  NOT NULL COMMENT 'GITLAB / GITHUB',
    project_id      BIGINT       NOT NULL,
    repo_path       VARCHAR(255) NOT NULL COMMENT 'group/repo',
    mr_iid          BIGINT       NOT NULL,
    commit_sha      VARCHAR(64)  NOT NULL,
    source_branch   VARCHAR(255),
    target_branch   VARCHAR(255),
    title           VARCHAR(512),
    author_username VARCHAR(128),

    status          VARCHAR(16)  NOT NULL COMMENT 'PENDING / REVIEWING / DONE / FAILED',
    conclusion      VARCHAR(16)  COMMENT 'APPROVE / NEEDS_FIX / BLOCK',
    confidence      DECIMAL(5,2) COMMENT '0-100',
    error_count     INT          DEFAULT 0,
    warning_count   INT          DEFAULT 0,
    info_count      INT          DEFAULT 0,
    duration_ms     BIGINT       COMMENT '审查耗时',

    triggered_at    DATETIME(3)  NOT NULL COMMENT 'webhook 触发时间',
    started_at      DATETIME(3),
    finished_at     DATETIME(3),
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    UNIQUE KEY uk_commit (platform, project_id, mr_iid, commit_sha),
    KEY idx_status_created (status, created_at),
    KEY idx_repo_mr (repo_path, mr_iid),
    KEY idx_conclusion (conclusion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查记录主表';

-- ===== 11.2 review_finding（审查发现表）=====
CREATE TABLE IF NOT EXISTS review_finding (
    id               BIGINT       PRIMARY KEY AUTO_INCREMENT,
    review_record_id BIGINT       NOT NULL COMMENT '外键 → review_record.id',
    file_path        VARCHAR(512) NOT NULL,
    line_number      INT          COMMENT 'NULL 表示文件级问题',
    severity         VARCHAR(16)  NOT NULL COMMENT 'ERROR / WARNING / INFO',
    rule_id          VARCHAR(64)  NOT NULL COMMENT '规则 ID 或 "llm_*"',
    message          TEXT         NOT NULL,
    suggestion       TEXT,
    source           VARCHAR(8)   NOT NULL COMMENT 'RULE / LLM',
    confidence       DECIMAL(5,2) DEFAULT 100.00,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    KEY idx_record (review_record_id),
    KEY idx_severity (severity),
    CONSTRAINT fk_finding_record FOREIGN KEY (review_record_id)
        REFERENCES review_record(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查发现表';

-- ===== 11.3 rule（规则配置表）=====
CREATE TABLE IF NOT EXISTS rule (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    rule_id      VARCHAR(64)  NOT NULL UNIQUE COMMENT 'no_test / sql_injection ...',
    name         VARCHAR(128) NOT NULL,
    description  TEXT,
    severity     VARCHAR(16)  NOT NULL COMMENT 'ERROR / WARNING / INFO',
    language     VARCHAR(32)  COMMENT '适用语言；NULL 表示全语言',
    rule_type    VARCHAR(16)  NOT NULL COMMENT 'BUILTIN / CUSTOM',
    params_json  TEXT         COMMENT '规则参数 JSON，如最大函数行数阈值',
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    KEY idx_enabled_language (enabled, language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查规则配置表';

-- ===== 11.4 review_statistic（按日聚合统计）=====
CREATE TABLE IF NOT EXISTS review_statistic (
    id               BIGINT      PRIMARY KEY AUTO_INCREMENT,
    stat_date        DATE        NOT NULL,
    platform         VARCHAR(16) NOT NULL,
    repo_path        VARCHAR(255) NOT NULL,
    total_count      INT         DEFAULT 0,
    approve_count    INT         DEFAULT 0,
    needs_fix_count  INT         DEFAULT 0,
    block_count      INT         DEFAULT 0,
    avg_duration_ms  BIGINT      DEFAULT 0,

    UNIQUE KEY uk_date_repo (stat_date, platform, repo_path),
    KEY idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按日聚合的审查统计';

-- ===== provider_config（LLM Provider 运行时配置，管理台可改）=====
-- api_key 加密存储（AES-GCM），接口永不回传明文，只回掩码（sk-**** + 后4位）
CREATE TABLE IF NOT EXISTS provider_config (
    provider     VARCHAR(32)  PRIMARY KEY COMMENT 'deepseek / qianwen / openai',
    base_url     VARCHAR(255),
    api_key_enc  VARCHAR(1024) COMMENT 'AES-GCM 加密后的 API Key（Base64）',
    api_key_tail VARCHAR(8)    COMMENT '掩码回显用（明文后 4 位）',
    model        VARCHAR(64),
    max_tokens   INT,
    temperature  DECIMAL(3,1),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM Provider 运行时配置';

-- ===== 预置 BUILTIN 规则（对齐 prompt 审查重点）=====
INSERT INTO rule (rule_id, name, description, severity, language, rule_type, params_json, enabled) VALUES
('sql_injection',       'SQL 注入风险',     '字符串拼接 SQL 语句，存在注入风险，应使用参数化绑定',        'ERROR',   NULL,   'BUILTIN', NULL, 1),
('hardcoded_secret',    '硬编码密钥',       '代码中硬编码 API Key / 密码 / Token 等敏感信息',              'ERROR',   NULL,   'BUILTIN', NULL, 1),
('empty_catch',         '空 catch 块',      'catch 块完全为空，异常被吞掉，问题难以排查',                  'ERROR',   'java', 'BUILTIN', NULL, 1),
('null_deref',          '明确空指针解引用', '代码中明确看到对可能为 null 的对象解引用',                    'ERROR',   'java', 'BUILTIN', NULL, 1),
('no_test',             '缺少测试',         '新增业务类（@Service/@Component/@Controller）但无对应测试',   'WARNING', 'java', 'BUILTIN', NULL, 1),
('transaction_missing', '事务边界缺失',     '批量写操作未声明 @Transactional，存在部分提交风险',           'WARNING', 'java', 'BUILTIN', NULL, 1),
('oversized_function',  '超大函数',         '函数行数超过阈值，建议拆分',                                  'WARNING', NULL,   'BUILTIN', '{"maxLines": 80}', 1),
('magic_number',        '魔法数字',         '未常量化的字面量数字（0/1/-1/2 除外）',                       'INFO',    NULL,   'BUILTIN', NULL, 1),
('todo_comment',        'TODO 待办',        '代码中遗留 TODO/FIXME 标记',                                  'INFO',    NULL,   'BUILTIN', NULL, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);
