-- 5A durable single-job reservation. Preserve revisions and execution journals.
CREATE TABLE IF NOT EXISTS sandbox_management_job (
  job_id CHAR(36) NOT NULL PRIMARY KEY,
  run_spec_key VARCHAR(128) NOT NULL,
  run_spec_revision INT NOT NULL,
  job_status ENUM('ACCEPTED','PREPARING','RUNNING','SUCCEEDED','FAILED','INTERRUPTED') NOT NULL,
  execution_id CHAR(36) DEFAULT NULL,
  worker_pid BIGINT DEFAULT NULL,
  worker_started_at VARCHAR(64) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  finished_at DATETIME(6) DEFAULT NULL,
  failure_code VARCHAR(80) DEFAULT NULL,
  CONSTRAINT fk_sandbox_job_revision FOREIGN KEY(run_spec_key,run_spec_revision)
    REFERENCES sandbox_run_spec_revision(run_spec_key,revision_no),
  CONSTRAINT fk_sandbox_job_execution FOREIGN KEY(execution_id) REFERENCES sandbox_execution(execution_id),
  KEY ix_sandbox_job_created(created_at,job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE sandbox_workspace_marker ADD COLUMN IF NOT EXISTS active_job_id CHAR(36) DEFAULT NULL;
UPDATE sandbox_workspace_marker SET control_schema_version='sandbox-control-schema/v7'
WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX';
