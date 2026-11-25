CREATE TABLE IF NOT EXISTS log_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    retention_days INT NOT NULL DEFAULT 7,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_log_groups_name ON log_groups(name);

CREATE TABLE IF NOT EXISTS log_streams (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES log_groups(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) NOT NULL DEFAULT 'DOCKER_CONTAINER',
    source_target VARCHAR(255),
    last_event_timestamp TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_group_stream_name UNIQUE (group_id, name)
);

CREATE INDEX IF NOT EXISTS idx_log_streams_group_id ON log_streams(group_id);

CREATE TABLE IF NOT EXISTS log_events (
    id UUID PRIMARY KEY,
    stream_id UUID NOT NULL REFERENCES log_streams(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    level VARCHAR(20) NOT NULL DEFAULT 'INFO',
    message TEXT NOT NULL,
    attributes JSONB
);

CREATE INDEX IF NOT EXISTS idx_log_events_stream_time ON log_events(stream_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_log_events_timestamp ON log_events(timestamp DESC);

CREATE TABLE IF NOT EXISTS instance_metrics (
    id UUID PRIMARY KEY,
    instance_id UUID,
    instance_name VARCHAR(100) NOT NULL,
    engine VARCHAR(50),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cpu_percent DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    memory_used_bytes BIGINT NOT NULL DEFAULT 0,
    memory_limit_bytes BIGINT NOT NULL DEFAULT 0,
    disk_used_bytes BIGINT NOT NULL DEFAULT 0,
    active_connections INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'HEALTHY'
);

CREATE INDEX IF NOT EXISTS idx_instance_metrics_name_time ON instance_metrics(instance_name, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_instance_metrics_time ON instance_metrics(timestamp DESC);
