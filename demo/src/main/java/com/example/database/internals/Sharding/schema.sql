-- Optional schema for JDBC shards (also auto-created by JdbcShardStore).
-- createdb shard0 && createdb shard1
-- psql -d shard0 -f schema.sql
-- psql -d shard1 -f schema.sql

CREATE TABLE IF NOT EXISTS users (
    id   TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS oo_heartbeats (
    user_id TEXT PRIMARY KEY,
    last_hb BIGINT NOT NULL
);
