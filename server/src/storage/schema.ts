export const SCHEMA_SQL = `
CREATE TABLE IF NOT EXISTS rules (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  schema_version INTEGER NOT NULL,
  version INTEGER NOT NULL,
  updated_at TEXT NOT NULL,
  hash TEXT NOT NULL,
  content TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS rules_backup (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  stamp TEXT NOT NULL,
  version INTEGER NOT NULL,
  content TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS stats_daily_apps (
  day TEXT NOT NULL,
  pkg TEXT NOT NULL,
  label TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  by_channel TEXT NOT NULL DEFAULT '{}',
  PRIMARY KEY (day, pkg)
);

CREATE TABLE IF NOT EXISTS stats_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  day TEXT NOT NULL,
  ts TEXT NOT NULL,
  pkg TEXT NOT NULL,
  label TEXT NOT NULL,
  channel TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS stats_dedup (
  dedup_key TEXT PRIMARY KEY,
  day TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stats_events_id ON stats_events(id DESC);
CREATE INDEX IF NOT EXISTS idx_stats_events_day ON stats_events(day);
CREATE INDEX IF NOT EXISTS idx_stats_dedup_day ON stats_dedup(day);
`;
