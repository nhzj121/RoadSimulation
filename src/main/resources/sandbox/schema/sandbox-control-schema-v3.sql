ALTER TABLE sandbox_workspace_marker
  MODIFY COLUMN workspace_state ENUM(
    'EMPTY','PREPARING','BASE_DATA_READY','SCENARIO_PREPARING','SCENARIO_DATA_READY',
    'RUN_SPEC_PREPARING','RUN_SPEC_READY','FAILED'
  ) NOT NULL,
  ADD COLUMN IF NOT EXISTS run_spec_key VARCHAR(128) DEFAULT NULL AFTER effective_scenario_data_sha256,
  ADD COLUMN IF NOT EXISTS run_spec_revision INT DEFAULT NULL AFTER run_spec_key,
  ADD COLUMN IF NOT EXISTS run_specification_sha256 CHAR(64) DEFAULT NULL AFTER run_spec_revision,
  ADD COLUMN IF NOT EXISTS random_protocol_id VARCHAR(64) DEFAULT NULL AFTER run_specification_sha256,
  ADD COLUMN IF NOT EXISTS root_seed_fingerprint CHAR(64) DEFAULT NULL AFTER random_protocol_id,
  ADD COLUMN IF NOT EXISTS resolved_vehicle_initial_state_sha256 CHAR(64) DEFAULT NULL AFTER root_seed_fingerprint,
  ADD COLUMN IF NOT EXISTS prepared_run_facts_sha256 CHAR(64) DEFAULT NULL AFTER resolved_vehicle_initial_state_sha256,
  ADD COLUMN IF NOT EXISTS failure_phase VARCHAR(64) DEFAULT NULL AFTER failure_message;

CREATE TABLE IF NOT EXISTS sandbox_run_spec (
  run_spec_key VARCHAR(128) NOT NULL,
  display_name VARCHAR(200) DEFAULT NULL,
  description VARCHAR(1000) DEFAULT NULL,
  draft_json LONGTEXT NOT NULL,
  draft_updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  row_version BIGINT NOT NULL DEFAULT 0,
  archived BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (run_spec_key),
  CONSTRAINT chk_sandbox_run_spec_draft_json CHECK (JSON_VALID(draft_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sandbox_run_spec_revision (
  run_spec_key VARCHAR(128) NOT NULL,
  revision_no INT NOT NULL,
  scenario_key VARCHAR(128) NOT NULL,
  scenario_revision INT NOT NULL,
  artifact_version VARCHAR(64) NOT NULL,
  random_protocol_id VARCHAR(64) NOT NULL,
  root_seed VARCHAR(19) NOT NULL,
  algorithm_profile_id VARCHAR(128) NOT NULL,
  run_specification_sha256 CHAR(64) NOT NULL,
  resolved_vehicle_initial_state_sha256 CHAR(64) NOT NULL,
  prepared_run_facts_sha256 CHAR(64) NOT NULL,
  revision_json LONGTEXT NOT NULL,
  published_at DATETIME(6) NOT NULL,
  archived BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (run_spec_key, revision_no),
  UNIQUE KEY uk_sandbox_run_spec_hash (run_spec_key, run_specification_sha256),
  CONSTRAINT fk_sandbox_run_revision_draft
    FOREIGN KEY (run_spec_key) REFERENCES sandbox_run_spec(run_spec_key),
  CONSTRAINT fk_sandbox_run_revision_scenario
    FOREIGN KEY (scenario_key, scenario_revision)
    REFERENCES sandbox_scenario_revision(scenario_key, revision_no),
  CONSTRAINT chk_sandbox_run_revision_json CHECK (JSON_VALID(revision_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sandbox_run_vehicle_initial_state (
  run_spec_key VARCHAR(128) NOT NULL,
  revision_no INT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  initialization_policy VARCHAR(64) NOT NULL,
  poi_id BIGINT NOT NULL,
  decision_domain VARCHAR(128) DEFAULT NULL,
  decision_key VARCHAR(512) DEFAULT NULL,
  derived_seed_hex CHAR(16) DEFAULT NULL,
  PRIMARY KEY (run_spec_key, revision_no, vehicle_id),
  CONSTRAINT fk_sandbox_run_vehicle_revision
    FOREIGN KEY (run_spec_key, revision_no)
    REFERENCES sandbox_run_spec_revision(run_spec_key, revision_no)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

UPDATE sandbox_workspace_marker
SET control_schema_version='sandbox-control-schema/v3'
WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX';
