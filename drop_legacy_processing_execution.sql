-- ============================================================
-- Optional cleanup for databases upgraded from the old model
-- ============================================================
-- The Java code no longer uses these columns/tables. This script
-- removes them. Back up the database before running it.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS shipment_upstream_relations;
DROP TABLE IF EXISTS processing_chain_predecessors;
DROP VIEW IF EXISTS v_processing_shipment;
DROP VIEW IF EXISTS v_processing_item;

DROP PROCEDURE IF EXISTS drop_foreign_keys_for_column;
DROP PROCEDURE IF EXISTS drop_column_if_exists;
DROP PROCEDURE IF EXISTS drop_index_if_exists;

DELIMITER $$

CREATE PROCEDURE drop_foreign_keys_for_column(IN table_name VARCHAR(64), IN column_name VARCHAR(64))
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE fk_name VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT CONSTRAINT_NAME
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name
          AND COLUMN_NAME = column_name
          AND REFERENCED_TABLE_NAME IS NOT NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    fk_loop: LOOP
        FETCH cur INTO fk_name;
        IF done THEN
            LEAVE fk_loop;
        END IF;
        SET @drop_sql = CONCAT(
            'ALTER TABLE `', table_name, '` DROP FOREIGN KEY `', fk_name, '`'
        );
        PREPARE stmt FROM @drop_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;
END$$

CREATE PROCEDURE drop_column_if_exists(IN table_name VARCHAR(64), IN column_name VARCHAR(64))
BEGIN
    DECLARE col_count INT;
    SELECT COUNT(*)
    INTO col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name
      AND COLUMN_NAME = column_name;

    IF col_count > 0 THEN
        SET @drop_sql = CONCAT(
            'ALTER TABLE `', table_name, '` DROP COLUMN `', column_name, '`'
        );
        PREPARE stmt FROM @drop_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE drop_index_if_exists(IN table_name VARCHAR(64), IN index_name VARCHAR(64))
BEGIN
    DECLARE idx_count INT;
    SELECT COUNT(*)
    INTO idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name
      AND INDEX_NAME = index_name;

    IF idx_count > 0 THEN
        SET @drop_sql = CONCAT(
            'ALTER TABLE `', table_name, '` DROP INDEX `', index_name, '`'
        );
        PREPARE stmt FROM @drop_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL drop_foreign_keys_for_column('shipment', 'processing_chain_id');
CALL drop_foreign_keys_for_column('shipment_item', 'stage_id');
CALL drop_foreign_keys_for_column('shipment_item', 'processing_poi_id');
CALL drop_foreign_keys_for_column('shipment_item', 'inbound_assignment_id');
CALL drop_foreign_keys_for_column('shipment_item', 'outbound_assignment_id');

CALL drop_index_if_exists('shipment', 'idx_processing_status');
CALL drop_index_if_exists('shipment', 'idx_processing_chain');
CALL drop_index_if_exists('shipment_item', 'idx_item_stage');
CALL drop_index_if_exists('shipment_item', 'idx_item_processing_status');
CALL drop_index_if_exists('shipment_item', 'idx_inbound_assignment');
CALL drop_index_if_exists('shipment_item', 'idx_outbound_assignment');

CALL drop_column_if_exists('shipment', 'is_processing_shipment');
CALL drop_column_if_exists('shipment', 'processing_chain_id');
CALL drop_column_if_exists('shipment', 'chain_code');
CALL drop_column_if_exists('shipment', 'chain_name');
CALL drop_column_if_exists('shipment', 'expected_yield_rate');
CALL drop_column_if_exists('shipment', 'expected_output_weight');
CALL drop_column_if_exists('shipment', 'actual_output_weight');
CALL drop_column_if_exists('shipment', 'processing_status');
CALL drop_column_if_exists('shipment', 'processing_start_time');
CALL drop_column_if_exists('shipment', 'processing_expected_finish_time');
CALL drop_column_if_exists('shipment', 'processing_actual_finish_time');

CALL drop_column_if_exists('shipment_item', 'stage_id');
CALL drop_column_if_exists('shipment_item', 'stage_order');
CALL drop_column_if_exists('shipment_item', 'stage_name');
CALL drop_column_if_exists('shipment_item', 'processing_poi_id');
CALL drop_column_if_exists('shipment_item', 'processing_status');
CALL drop_column_if_exists('shipment_item', 'processed_weight');
CALL drop_column_if_exists('shipment_item', 'progress_percent');
CALL drop_column_if_exists('shipment_item', 'processing_start_time');
CALL drop_column_if_exists('shipment_item', 'processing_end_time');
CALL drop_column_if_exists('shipment_item', 'inbound_assignment_id');
CALL drop_column_if_exists('shipment_item', 'outbound_assignment_id');

CALL drop_column_if_exists('processing_chain', 'total_processing_time_minutes');
CALL drop_column_if_exists('processing_chain', 'input_weight_per_cycle');
CALL drop_column_if_exists('processing_chain', 'output_weight_per_cycle');
CALL drop_column_if_exists('processing_chain', 'yield_rate');
CALL drop_column_if_exists('processing_chain', 'predecessor_chain_ids');
CALL drop_column_if_exists('processing_chain', 'merge_stage_id');
CALL drop_column_if_exists('processing_chain', 'input_materials');
CALL drop_column_if_exists('processing_chain', 'transport_distance_meters');
CALL drop_column_if_exists('processing_chain', 'transport_driving_seconds');
CALL drop_column_if_exists('processing_chain', 'processing_seconds');
CALL drop_column_if_exists('processing_chain', 'waiting_seconds');
CALL drop_column_if_exists('processing_chain', 'total_elapsed_seconds');

CALL drop_column_if_exists('processing_stage', 'transport_distance_meters');
CALL drop_column_if_exists('processing_stage', 'transport_driving_seconds');
CALL drop_column_if_exists('processing_stage', 'processing_seconds');
CALL drop_column_if_exists('processing_stage', 'waiting_seconds');
CALL drop_column_if_exists('processing_stage', 'total_elapsed_seconds');

DROP PROCEDURE IF EXISTS drop_foreign_keys_for_column;
DROP PROCEDURE IF EXISTS drop_column_if_exists;
DROP PROCEDURE IF EXISTS drop_index_if_exists;

SET FOREIGN_KEY_CHECKS = 1;
