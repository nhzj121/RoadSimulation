CREATE TABLE IF NOT EXISTS sandbox_optimization_job (
  job_id CHAR(36) PRIMARY KEY,
  configuration_sha256 CHAR(64) NOT NULL,
  problem_sha256 CHAR(64) DEFAULT NULL,
  preparation_complete BOOLEAN NOT NULL DEFAULT FALSE,
  best_execution_id CHAR(36) DEFAULT NULL,
  stop_reason VARCHAR(64) DEFAULT NULL,
  CONSTRAINT fk_optimization_job FOREIGN KEY(job_id) REFERENCES sandbox_management_job(job_id),
  CONSTRAINT fk_optimization_best FOREIGN KEY(best_execution_id) REFERENCES sandbox_execution(execution_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sandbox_optimization_attempt (
  execution_id CHAR(36) PRIMARY KEY,
  job_id CHAR(36) NOT NULL,
  attempt_kind VARCHAR(24) NOT NULL,
  manifest_sha256 CHAR(64) NOT NULL,
  last_tick_sha256 CHAR(64) DEFAULT NULL,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_optimization_attempt_job FOREIGN KEY(job_id) REFERENCES sandbox_optimization_job(job_id),
  CONSTRAINT fk_optimization_attempt_execution FOREIGN KEY(execution_id) REFERENCES sandbox_execution(execution_id)
) ENGINE=InnoDB;

ALTER TABLE sandbox_run_spec ADD COLUMN IF NOT EXISTS internal_input BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE sandbox_workspace_marker SET control_schema_version='sandbox-control-schema/v10'
WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX';
