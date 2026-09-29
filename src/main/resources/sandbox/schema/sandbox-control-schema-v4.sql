-- Additive upgrade: no published v1 JSON or fingerprints are modified.
ALTER TABLE sandbox_workspace_marker
  ADD COLUMN IF NOT EXISTS run_artifact_version VARCHAR(64) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS weather_timeline_sha256 CHAR(64) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS event_configuration_sha256 CHAR(64) DEFAULT NULL;
UPDATE sandbox_workspace_marker SET control_schema_version='sandbox-control-schema/v4'
WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX';
