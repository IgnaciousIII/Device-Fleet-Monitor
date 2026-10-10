CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    last_heartbeat TEXT,
    last_reported_status TEXT
);
