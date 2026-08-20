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
    CONSTRAINT ck_monitor_alert_threshold CHECK (warning_threshold >= 0 AND critical_threshold <= 100 AND warning_threshold < critical_threshold)
);

CREATE INDEX IF NOT EXISTS idx_monitor_alert_rule_node ON monitor_alert_rule(node_client_id);
CREATE INDEX IF NOT EXISTS idx_monitor_alert_rule_enabled ON monitor_alert_rule(enabled);

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
    duration_seconds BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_monitor_alert_event_status ON monitor_alert_event(status);
CREATE INDEX IF NOT EXISTS idx_monitor_alert_event_rule_client ON monitor_alert_event(rule_id, client_id, status);
