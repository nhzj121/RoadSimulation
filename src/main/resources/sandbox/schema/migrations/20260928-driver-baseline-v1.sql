-- Manual, idempotent MariaDB migration for the driver baseline integration.
-- Apply with an administrator account before switching ordinary runtime to ddl-auto=validate.
-- The sandbox preparation workflow does not execute this file; it rebuilds from sandbox-schema-v1.sql.

ALTER TABLE `driver`
  MODIFY COLUMN `current_status`
    enum('ASSIGNED','IDLE','MAINTENANCE','OFF','REJECTING') DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS `pref_cargo` varchar(50) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS `pref_max_distance_km` double DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS `pref_max_weight_tons` double DEFAULT NULL;

CREATE TABLE IF NOT EXISTS `assignment_driver_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `action` enum('BIND','RELEASE') NOT NULL,
  `actor` varchar(50) DEFAULT NULL,
  `assignment_id` bigint(20) NOT NULL,
  `created_time` datetime(6) DEFAULT NULL,
  `driver_id` bigint(20) NOT NULL,
  `driver_name` varchar(50) DEFAULT NULL,
  `from_status` varchar(20) DEFAULT NULL,
  `reason` varchar(100) DEFAULT NULL,
  `sim_time` datetime(6) DEFAULT NULL,
  `to_status` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX IF NOT EXISTS `idx_assignment_driver_history_assignment`
  ON `assignment_driver_history` (`assignment_id`);
CREATE INDEX IF NOT EXISTS `idx_assignment_driver_history_driver`
  ON `assignment_driver_history` (`driver_id`);
