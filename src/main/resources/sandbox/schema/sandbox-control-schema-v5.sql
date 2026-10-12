-- Additive execution control upgrade. Never rebuild published revisions or business tables.
ALTER TABLE sandbox_workspace_marker
  MODIFY COLUMN workspace_state ENUM(
    'EMPTY','PREPARING','BASE_DATA_READY','SCENARIO_PREPARING','SCENARIO_DATA_READY',
    'RUN_SPEC_PREPARING','RUN_SPEC_READY','EXECUTION_RUNNING','EXECUTION_COMPLETED','FAILED'
  ) NOT NULL,
  ADD COLUMN IF NOT EXISTS active_execution_id CHAR(36) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS sandbox_execution (
  execution_id CHAR(36) NOT NULL PRIMARY KEY,
  run_spec_key VARCHAR(128) NOT NULL,
  run_spec_revision INT NOT NULL,
  baseline_id VARCHAR(128) NOT NULL,
  deterministic_run_id VARCHAR(100) NOT NULL,
  prepared_run_facts_sha256 CHAR(64) NOT NULL,
  requested_loops INT NOT NULL,
  completed_loops INT NOT NULL DEFAULT 0,
  failed_loop_index INT DEFAULT NULL,
  execution_status ENUM('CREATED','RUNNING','COMPLETED','FAILED') NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  finished_at DATETIME(6) DEFAULT NULL,
  failure_code VARCHAR(80) DEFAULT NULL,
  failure_message VARCHAR(1000) DEFAULT NULL,
  manifest_json LONGTEXT NOT NULL,
  CONSTRAINT fk_sandbox_execution_revision FOREIGN KEY (run_spec_key,run_spec_revision)
    REFERENCES sandbox_run_spec_revision(run_spec_key,revision_no),
  CONSTRAINT chk_sandbox_execution_manifest CHECK (JSON_VALID(manifest_json)),
  CONSTRAINT chk_sandbox_execution_loops CHECK (requested_loops>0 AND completed_loops>=0
    AND completed_loops<=requested_loops)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

UPDATE sandbox_workspace_marker SET control_schema_version='sandbox-control-schema/v5'
WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX';
