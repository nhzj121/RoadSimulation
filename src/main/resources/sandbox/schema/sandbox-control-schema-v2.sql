ALTER TABLE sandbox_workspace_marker
  MODIFY COLUMN workspace_state ENUM(
    'EMPTY','PREPARING','BASE_DATA_READY','SCENARIO_PREPARING','SCENARIO_DATA_READY','FAILED'
  ) NOT NULL,
  ADD COLUMN IF NOT EXISTS control_schema_version VARCHAR(64) DEFAULT NULL AFTER schema_version,
  ADD COLUMN IF NOT EXISTS scenario_key VARCHAR(128) DEFAULT NULL AFTER eligibility_policy_version,
  ADD COLUMN IF NOT EXISTS scenario_revision INT DEFAULT NULL AFTER scenario_key,
  ADD COLUMN IF NOT EXISTS scenario_definition_sha256 CHAR(64) DEFAULT NULL AFTER scenario_revision,
  ADD COLUMN IF NOT EXISTS effective_scenario_data_sha256 CHAR(64) DEFAULT NULL AFTER scenario_definition_sha256;

CREATE TABLE IF NOT EXISTS sandbox_scenario (
  scenario_key VARCHAR(128) NOT NULL,
  display_name VARCHAR(200) DEFAULT NULL,
  description VARCHAR(1000) DEFAULT NULL,
  draft_json LONGTEXT NOT NULL,
  draft_updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  row_version BIGINT NOT NULL DEFAULT 0,
  archived BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (scenario_key),
  CONSTRAINT chk_sandbox_scenario_draft_json CHECK (JSON_VALID(draft_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sandbox_scenario_revision (
  scenario_key VARCHAR(128) NOT NULL,
  revision_no INT NOT NULL,
  artifact_version VARCHAR(64) NOT NULL,
  baseline_id VARCHAR(128) NOT NULL,
  scenario_definition_sha256 CHAR(64) NOT NULL,
  base_data_projection_sha256 CHAR(64) NOT NULL,
  effective_scenario_data_sha256 CHAR(64) NOT NULL,
  revision_json LONGTEXT NOT NULL,
  published_at DATETIME(6) NOT NULL,
  archived BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (scenario_key, revision_no),
  UNIQUE KEY uk_sandbox_scenario_definition (scenario_key, scenario_definition_sha256),
  CONSTRAINT fk_sandbox_scenario_revision_scenario
    FOREIGN KEY (scenario_key) REFERENCES sandbox_scenario(scenario_key),
  CONSTRAINT chk_sandbox_scenario_revision_json CHECK (JSON_VALID(revision_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

UPDATE sandbox_workspace_marker
SET control_schema_version='sandbox-control-schema/v2'
WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX';
