-- 5B-2 durable start idempotency. Historical jobs retain NULL; revisions and ledgers are untouched.
ALTER TABLE sandbox_management_job
  ADD COLUMN IF NOT EXISTS client_request_id CHAR(36) DEFAULT NULL,
  ADD UNIQUE INDEX IF NOT EXISTS uk_sandbox_start_request (client_request_id);
UPDATE sandbox_workspace_marker SET control_schema_version='sandbox-control-schema/v9'
WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX';
