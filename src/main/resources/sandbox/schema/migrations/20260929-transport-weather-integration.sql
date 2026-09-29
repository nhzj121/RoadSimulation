-- Explicit, additive MariaDB 10.4 migration. NEVER run during Spring startup.
-- Operator must verify/select the intended schema and back it up before applying.
-- No USE, DROP TABLE, or data mutation. Existing historical JSON is preserved.
ALTER TABLE driver ADD COLUMN IF NOT EXISTS reserved_replacement_event_id BIGINT DEFAULT NULL;
ALTER TABLE vehicle
  MODIFY COLUMN current_status ENUM('BREAKDOWN','IDLE','LOADING','ORDER_DRIVING','TRANSPORT_DRIVING','UNLOADING','WAITING','SCRAPPED','RESERVED_REPLACEMENT') DEFAULT NULL,
  MODIFY COLUMN previous_status ENUM('BREAKDOWN','IDLE','LOADING','ORDER_DRIVING','TRANSPORT_DRIVING','UNLOADING','WAITING','SCRAPPED','RESERVED_REPLACEMENT') DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS replacement_reservation_event_id BIGINT DEFAULT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_vehicle_replacement_reservation ON vehicle(replacement_reservation_event_id);

-- Event/weather compatibility tables are empty after all sandbox preparation stages.
CREATE TABLE IF NOT EXISTS `weather_scenario` (
  `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, `name` varchar(255),
  `definition_json` longtext NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `weather_run` (
  `id` varchar(255) NOT NULL PRIMARY KEY, `scenario_id` bigint,
  `external_experiment_id` varchar(255), `started_at` datetime(6), `ended_at` datetime(6),
  `manually_intervened` bit NOT NULL, `frozen_scenario_json` longtext,
  `frozen_event_configuration_json` longtext, `weather_timeline_sha256` varchar(64),
  `event_history_json` longtext, `driving_history_json` longtext,
  `execution_segment_history_json` longtext, `replacement_attempt_history_json` longtext
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `driving_progress` (
  `phase_key` varchar(240) NOT NULL PRIMARY KEY, `run_id` varchar(255), `vehicle_id` bigint,
  `assignment_id` bigint, `leg_index` int, `driving_status` varchar(255),
  `phase_start` datetime(6), `last_settled_time` datetime(6), `initial_work_seconds` double NOT NULL,
  `remaining_work_seconds` double NOT NULL, `affected_seconds` double NOT NULL,
  `lost_work_seconds` double NOT NULL, `model_completed_time` datetime(6), `observed_completed_time` datetime(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `transport_random_event` (
  `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, `run_id` varchar(36),
  `trigger_loop_index` int, `random_protocol_id` varchar(80),
  `event_type` varchar(40) NOT NULL, `status` varchar(20) NOT NULL, `trigger_source` varchar(20) NOT NULL,
  `vehicle_id` bigint NOT NULL, `license_plate` varchar(50), `assignment_id` bigint NOT NULL,
  `start_time` datetime(6) NOT NULL, `planned_end_time` datetime(6), `resolved_time` datetime(6),
  `speed_factor` double, `previous_vehicle_status` varchar(30), `remaining_status_seconds` bigint,
  `delay_seconds` bigint, `random_seed` bigint, `description` varchar(255),
  `breakdown_level` varchar(30), `breakdown_phase` varchar(30), `rescue_wait_minutes` int,
  `repair_minutes` int, `repair_start_time` datetime(6), `recovery_processed_time` datetime(6),
  `recovery_outcome` varchar(80), `breakdown_rule_version` varchar(40), `original_assignment_status` varchar(20),
  `original_leg_index` int, `original_driving_phase_key` varchar(240), `replacement_wait_minutes` int,
  `replacement_vehicle_id` bigint, `original_driver_id` bigint, `replacement_driver_id` bigint,
  `replacement_license_plate` varchar(50), `replacement_selected_time` datetime(6),
  `replacement_ready_time` datetime(6), `replacement_processed_time` datetime(6), `replacement_outcome` varchar(80),
  `required_load` double, `required_volume` double,
  UNIQUE KEY `uk_transport_event_auto_tick` (`run_id`,`vehicle_id`,`trigger_loop_index`),
  KEY `idx_transport_event_status` (`status`), KEY `idx_transport_event_vehicle_status` (`vehicle_id`,`status`),
  KEY `idx_transport_event_assignment` (`assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `vehicle_replacement_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, `run_id` varchar(36), `event_id` bigint NOT NULL,
  `assignment_id` bigint NOT NULL, `original_vehicle_id` bigint NOT NULL, `original_license_plate` varchar(50),
  `replacement_vehicle_id` bigint NOT NULL, `replacement_license_plate` varchar(50),
  `original_driver_id` bigint, `replacement_driver_id` bigint, `selected_time` datetime(6) NOT NULL,
  `ready_time` datetime(6) NOT NULL, `handoff_time` datetime(6), `wait_minutes` int NOT NULL,
  `required_load` double NOT NULL, `required_volume` double NOT NULL, `status` varchar(20) NOT NULL,
  `outcome` varchar(80), `rule_version` varchar(40), KEY `idx_replacement_attempt_event` (`event_id`),
  KEY `idx_replacement_attempt_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE IF NOT EXISTS `transport_execution_segment` (
  `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, `run_id` varchar(36) NOT NULL,
  `assignment_id` bigint NOT NULL, `leg_id` bigint NOT NULL, `loop_index` int NOT NULL, `fragment_index` int NOT NULL,
  `vehicle_id` bigint NOT NULL, `driver_id` bigint NOT NULL, `from_sim_time` datetime(6) NOT NULL,
  `to_sim_time` datetime(6) NOT NULL, `distance_meters` double NOT NULL, `driving_seconds` bigint NOT NULL,
  `capacity_tonnes` double DEFAULT NULL, `load_tonnes` double NOT NULL, `load_state` varchar(20) NOT NULL,
  `travel_time_factor` double NOT NULL, `energy_liters` double NOT NULL, `emission_kg` double NOT NULL,
  `energy_valid` bit NOT NULL, `emission_model_id` varchar(80), `vehicle_class_code` varchar(40),
  UNIQUE KEY `uk_execution_segment_tick` (`leg_id`,`loop_index`,`fragment_index`),
  KEY `idx_execution_segment_run` (`run_id`), KEY `idx_execution_segment_vehicle` (`vehicle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- Preserve the existing master rule: invalid evaluation metadata must not stop transport.
ALTER TABLE transport_execution_segment MODIFY COLUMN capacity_tonnes DOUBLE DEFAULT NULL;

ALTER TABLE weather_run
  ADD COLUMN IF NOT EXISTS frozen_scenario_json LONGTEXT,
  ADD COLUMN IF NOT EXISTS frozen_event_configuration_json LONGTEXT,
  ADD COLUMN IF NOT EXISTS weather_timeline_sha256 VARCHAR(64),
  ADD COLUMN IF NOT EXISTS event_history_json LONGTEXT,
  ADD COLUMN IF NOT EXISTS driving_history_json LONGTEXT,
  ADD COLUMN IF NOT EXISTS replacement_attempt_history_json LONGTEXT,
  ADD COLUMN IF NOT EXISTS execution_segment_history_json LONGTEXT;
ALTER TABLE transport_random_event
  ADD COLUMN IF NOT EXISTS trigger_loop_index INT,
  ADD COLUMN IF NOT EXISTS random_protocol_id VARCHAR(80),
  ADD COLUMN IF NOT EXISTS original_driver_id BIGINT,
  ADD COLUMN IF NOT EXISTS replacement_driver_id BIGINT,
  MODIFY COLUMN planned_end_time DATETIME(6) DEFAULT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_transport_event_auto_tick ON transport_random_event(run_id,vehicle_id,trigger_loop_index);
ALTER TABLE vehicle_replacement_attempt
  ADD COLUMN IF NOT EXISTS original_driver_id BIGINT,
  ADD COLUMN IF NOT EXISTS replacement_driver_id BIGINT;
