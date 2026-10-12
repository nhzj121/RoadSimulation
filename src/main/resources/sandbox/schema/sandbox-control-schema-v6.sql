-- Append-only execution journal. Preparation must preserve both execution tables.
ALTER TABLE sandbox_execution ADD COLUMN IF NOT EXISTS manifest_sha256 CHAR(64) DEFAULT NULL;
ALTER TABLE sandbox_execution ADD COLUMN IF NOT EXISTS failure_phase VARCHAR(30) DEFAULT NULL;
CREATE TABLE IF NOT EXISTS sandbox_execution_tick (
  execution_id CHAR(36) NOT NULL,
  loop_index INT NOT NULL,
  tick_start DATETIME(6) NOT NULL,
  tick_end DATETIME(6) NOT NULL,
  facts_json LONGTEXT NOT NULL,
  facts_sha256 CHAR(64) NOT NULL,
  business_facts_json LONGTEXT NOT NULL,
  business_facts_sha256 CHAR(64) NOT NULL,
  evaluation_json LONGTEXT NOT NULL,
  evaluation_sha256 CHAR(64) NOT NULL,
  previous_tick_sha256 CHAR(64) NOT NULL,
  tick_sha256 CHAR(64) NOT NULL,
  recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY(execution_id,loop_index),
  CONSTRAINT fk_sandbox_tick_execution FOREIGN KEY(execution_id) REFERENCES sandbox_execution(execution_id),
  CONSTRAINT chk_sandbox_tick_index CHECK(loop_index>=0 AND tick_end>tick_start),
  CONSTRAINT chk_sandbox_tick_json CHECK(JSON_VALID(facts_json) AND JSON_VALID(business_facts_json) AND JSON_VALID(evaluation_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
UPDATE sandbox_workspace_marker SET control_schema_version='sandbox-control-schema/v6'
WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX';
