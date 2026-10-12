-- 5A lifecycle: safe cancellation and explicitly inspected abnormal closure.
-- Additive upgrade; retain all revisions, jobs and execution journals.
ALTER TABLE sandbox_management_job
  MODIFY COLUMN job_status ENUM('ACCEPTED','PREPARING','RUNNING','CANCEL_REQUESTED',
    'FINALIZING','SUCCEEDED','FAILED','INTERRUPTED','CANCELLED','ABORTED') NOT NULL,
  ADD COLUMN IF NOT EXISTS cancel_requested_at DATETIME(6) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS closure_report_json LONGTEXT DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS closed_at DATETIME(6) DEFAULT NULL;
ALTER TABLE sandbox_execution
  MODIFY COLUMN execution_status ENUM('CREATED','RUNNING','COMPLETED','FAILED','CANCELLED','INTERRUPTED') NOT NULL;
ALTER TABLE sandbox_workspace_marker
  MODIFY COLUMN workspace_state ENUM('EMPTY','PREPARING','BASE_DATA_READY','SCENARIO_PREPARING',
    'SCENARIO_DATA_READY','RUN_SPEC_PREPARING','RUN_SPEC_READY','EXECUTION_RUNNING',
    'EXECUTION_COMPLETED','EXECUTION_CANCELLED','FAILED') NOT NULL;
UPDATE sandbox_workspace_marker SET control_schema_version='sandbox-control-schema/v8'
WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX';
