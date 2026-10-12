CREATE TABLE IF NOT EXISTS monitor_node (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(128),
    description VARCHAR(500),
    environment VARCHAR(64),
    location VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    display_system BOOLEAN NOT NULL DEFAULT TRUE,
    display_docker BOOLEAN NOT NULL DEFAULT TRUE,
    display_microservice BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS monitor_alert_rule (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    node_client_id VARCHAR(128),
    metric VARCHAR(32) NOT NULL,
    warning_threshold NUMERIC(6, 2) NOT NULL DEFAULT 70,
    critical_threshold NUMERIC(6, 2) NOT NULL DEFAULT 90,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_monitor_alert_threshold CHECK (warning_threshold >= 0 AND warning_threshold < critical_threshold)
);

ALTER TABLE monitor_alert_rule DROP CONSTRAINT IF EXISTS ck_monitor_alert_threshold;
ALTER TABLE monitor_alert_rule ADD CONSTRAINT ck_monitor_alert_threshold
    CHECK (warning_threshold >= 0 AND warning_threshold < critical_threshold);
ALTER TABLE monitor_alert_rule ALTER COLUMN warning_threshold TYPE NUMERIC(12, 2);
ALTER TABLE monitor_alert_rule ALTER COLUMN critical_threshold TYPE NUMERIC(12, 2);

CREATE INDEX IF NOT EXISTS idx_monitor_alert_rule_node ON monitor_alert_rule(node_client_id);
CREATE INDEX IF NOT EXISTS idx_monitor_alert_rule_enabled ON monitor_alert_rule(enabled);

INSERT INTO monitor_alert_rule (
    name, node_client_id, metric, warning_threshold, critical_threshold, enabled
)
SELECT 'CPU使用率过高', NULL, 'CPU', 80, 90, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM monitor_alert_rule WHERE metric = 'CPU'
);

INSERT INTO monitor_alert_rule (
    name, node_client_id, metric, warning_threshold, critical_threshold, enabled
)
SELECT '内存使用率过高', NULL, 'MEMORY', 80, 90, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM monitor_alert_rule WHERE metric = 'MEMORY'
);

INSERT INTO monitor_alert_rule (
    name, node_client_id, metric, warning_threshold, critical_threshold, enabled
)
SELECT '磁盘使用率过高', NULL, 'DISK', 80, 90, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM monitor_alert_rule WHERE metric = 'DISK'
);

INSERT INTO monitor_alert_rule (
    name, node_client_id, metric, warning_threshold, critical_threshold, enabled
)
SELECT '瀚高大表全表查询', NULL, 'LARGE_TABLE_QUERY', 1, 5, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM monitor_alert_rule WHERE metric = 'LARGE_TABLE_QUERY'
);

INSERT INTO monitor_alert_rule (
    name, node_client_id, metric, warning_threshold, critical_threshold, enabled
)
SELECT '瀚高数据库登录失败', NULL, 'LOGIN_FAILURE', 1, 5, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM monitor_alert_rule WHERE metric = 'LOGIN_FAILURE'
);

CREATE TABLE IF NOT EXISTS monitor_alert_event (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES monitor_alert_rule(id) ON DELETE CASCADE,
    client_id VARCHAR(128) NOT NULL,
    node_name VARCHAR(128),
    metric VARCHAR(32) NOT NULL,
    value NUMERIC(10, 2) NOT NULL,
    level VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    message VARCHAR(500),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    duration_seconds BIGINT NOT NULL DEFAULT 0,
    requires_acknowledgement BOOLEAN NOT NULL DEFAULT TRUE,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_at TIMESTAMP
);

-- 兼容旧告警：历史已恢复记录维持原展示状态；迁移时仍活动的记录和后续新记录必须人工确认。
ALTER TABLE monitor_alert_event
    ADD COLUMN IF NOT EXISTS requires_acknowledgement BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE monitor_alert_event
SET requires_acknowledgement = TRUE
WHERE status = 'ACTIVE';
ALTER TABLE monitor_alert_event
    ALTER COLUMN requires_acknowledgement SET DEFAULT TRUE;
ALTER TABLE monitor_alert_event ADD COLUMN IF NOT EXISTS acknowledged BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitor_alert_event ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP;

-- 历史系统指标告警统一转换为面向用户的中文文案，保证新旧记录展示一致。
UPDATE monitor_alert_event
SET message = COALESCE(NULLIF(node_name, ''), client_id) || '机器的'
        || CASE metric
            WHEN 'CPU' THEN 'CPU使用率'
            WHEN 'MEMORY' THEN '内存使用率'
            WHEN 'DISK' THEN '硬盘使用率'
            WHEN 'JVM' THEN 'JVM堆内存使用率'
        END
        || '当前为'
        || TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(value AS VARCHAR)))
        || '%'
WHERE metric IN ('CPU', 'MEMORY', 'DISK', 'JVM');

CREATE INDEX IF NOT EXISTS idx_monitor_alert_event_status ON monitor_alert_event(status);
CREATE INDEX IF NOT EXISTS idx_monitor_alert_event_rule_client ON monitor_alert_event(rule_id, client_id, status);
CREATE INDEX IF NOT EXISTS idx_monitor_alert_event_started_at ON monitor_alert_event(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_monitor_alert_event_acknowledged
    ON monitor_alert_event(acknowledged, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_monitor_alert_event_pending
    ON monitor_alert_event(requires_acknowledgement, acknowledged, started_at DESC);
