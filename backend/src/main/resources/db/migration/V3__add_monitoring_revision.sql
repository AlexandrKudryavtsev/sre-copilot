-- Reject in-flight collection results after monitoring is disabled or restarted.
ALTER TABLE projects ADD COLUMN monitoring_revision BIGINT NOT NULL DEFAULT 0;
