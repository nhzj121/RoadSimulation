param(
    [string]$MysqlBin = 'E:\XAMPP\mysql\bin\mysql.exe',
    [string]$AdminUser = 'root'
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:SANDBOX_DB_PASSWORD)) {
    throw 'Set SANDBOX_DB_PASSWORD before provisioning. The password is never stored in the repository.'
}
if ($env:SANDBOX_DB_PASSWORD.Contains("`r") -or $env:SANDBOX_DB_PASSWORD.Contains("`n") -or
    $env:SANDBOX_DB_PASSWORD.Contains([char]0)) {
    throw 'SANDBOX_DB_PASSWORD must not contain line breaks or NUL characters.'
}

if (-not (Test-Path -LiteralPath $MysqlBin)) {
    throw "MariaDB client not found: $MysqlBin"
}

function ConvertTo-SqlString([string]$Value) {
    return $Value.Replace('\', '\\').Replace("'", "''")
}

$runtimePassword = ConvertTo-SqlString $env:SANDBOX_DB_PASSWORD
$sql = @"
CREATE DATABASE IF NOT EXISTS vehicle_scheduler_sandbox
  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'road_sandbox_runtime'@'localhost' IDENTIFIED BY '$runtimePassword';
ALTER USER 'road_sandbox_runtime'@'localhost' IDENTIFIED BY '$runtimePassword';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'road_sandbox_runtime'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, REFERENCES,
      CREATE TEMPORARY TABLES, LOCK TABLES
ON vehicle_scheduler_sandbox.* TO 'road_sandbox_runtime'@'localhost';

USE vehicle_scheduler_sandbox;
CREATE TABLE IF NOT EXISTS sandbox_workspace_marker (
  marker_id TINYINT NOT NULL,
  workspace_kind VARCHAR(64) NOT NULL,
  schema_version VARCHAR(64) DEFAULT NULL,
  baseline_id VARCHAR(128) DEFAULT NULL,
  restoration_payload_sha256 CHAR(64) DEFAULT NULL,
  simulation_facts_sha256 CHAR(64) DEFAULT NULL,
  effective_base_data_sha256 CHAR(64) DEFAULT NULL,
  eligibility_policy_version VARCHAR(64) DEFAULT NULL,
  workspace_state ENUM('EMPTY','PREPARING','BASE_DATA_READY','SCENARIO_PREPARING','SCENARIO_DATA_READY',
    'RUN_SPEC_PREPARING','RUN_SPEC_READY','FAILED') NOT NULL,
  prepared_at DATETIME(6) DEFAULT NULL,
  failure_code VARCHAR(80) DEFAULT NULL,
  failure_message VARCHAR(1000) DEFAULT NULL,
  PRIMARY KEY (marker_id),
  CONSTRAINT chk_sandbox_marker_id CHECK (marker_id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT INTO sandbox_workspace_marker(marker_id, workspace_kind, workspace_state)
VALUES (1, 'ROAD_SIMULATION_SANDBOX', 'EMPTY')
ON DUPLICATE KEY UPDATE workspace_kind=VALUES(workspace_kind);

ALTER TABLE sandbox_workspace_marker
  MODIFY COLUMN workspace_state ENUM(
    'EMPTY','PREPARING','BASE_DATA_READY','SCENARIO_PREPARING','SCENARIO_DATA_READY',
    'RUN_SPEC_PREPARING','RUN_SPEC_READY','FAILED'
  ) NOT NULL,
  ADD COLUMN IF NOT EXISTS control_schema_version VARCHAR(64) DEFAULT NULL AFTER schema_version,
  ADD COLUMN IF NOT EXISTS scenario_key VARCHAR(128) DEFAULT NULL AFTER eligibility_policy_version,
  ADD COLUMN IF NOT EXISTS scenario_revision INT DEFAULT NULL AFTER scenario_key,
  ADD COLUMN IF NOT EXISTS scenario_definition_sha256 CHAR(64) DEFAULT NULL AFTER scenario_revision,
  ADD COLUMN IF NOT EXISTS effective_scenario_data_sha256 CHAR(64) DEFAULT NULL AFTER scenario_definition_sha256,
  ADD COLUMN IF NOT EXISTS run_spec_key VARCHAR(128) DEFAULT NULL AFTER effective_scenario_data_sha256,
  ADD COLUMN IF NOT EXISTS run_spec_revision INT DEFAULT NULL AFTER run_spec_key,
  ADD COLUMN IF NOT EXISTS run_specification_sha256 CHAR(64) DEFAULT NULL AFTER run_spec_revision,
  ADD COLUMN IF NOT EXISTS random_protocol_id VARCHAR(64) DEFAULT NULL AFTER run_specification_sha256,
  ADD COLUMN IF NOT EXISTS root_seed_fingerprint CHAR(64) DEFAULT NULL AFTER random_protocol_id,
  ADD COLUMN IF NOT EXISTS resolved_vehicle_initial_state_sha256 CHAR(64) DEFAULT NULL AFTER root_seed_fingerprint,
  ADD COLUMN IF NOT EXISTS prepared_run_facts_sha256 CHAR(64) DEFAULT NULL AFTER resolved_vehicle_initial_state_sha256,
  ADD COLUMN IF NOT EXISTS failure_phase VARCHAR(64) DEFAULT NULL AFTER failure_message;

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
"@

$oldAdminPassword = $env:MYSQL_PWD
try {
    if (-not [string]::IsNullOrWhiteSpace($env:MYSQL_ADMIN_PASSWORD)) {
        $env:MYSQL_PWD = $env:MYSQL_ADMIN_PASSWORD
    } else {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    $sql | & $MysqlBin --user=$AdminUser --default-character-set=utf8mb4 --batch
    if ($LASTEXITCODE -ne 0) {
        throw "MariaDB provisioning failed with exit code $LASTEXITCODE"
    }
} finally {
    if ($null -eq $oldAdminPassword) {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    } else {
        $env:MYSQL_PWD = $oldAdminPassword
    }
}

Write-Host 'Provisioned vehicle_scheduler_sandbox and road_sandbox_runtime.'
