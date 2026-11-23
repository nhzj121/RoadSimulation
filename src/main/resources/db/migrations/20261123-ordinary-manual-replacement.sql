-- Apply to the ordinary simulation database. The sandbox schema is versioned separately.
CREATE TABLE IF NOT EXISTS ordinary_manual_replacement (
    event_id BIGINT NOT NULL PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    request_id CHAR(36) NOT NULL,
    original_vehicle_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    longitude DOUBLE NOT NULL,
    latitude DOUBLE NOT NULL,
    next_stop_index INT NOT NULL,
    phase VARCHAR(24) NOT NULL,
    attempt_id BIGINT NULL,
    spare_longitude DOUBLE NULL,
    spare_latitude DOUBLE NULL,
    restored BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(80) NULL,
    UNIQUE KEY uk_manual_replacement_request (run_id, request_id),
    KEY idx_manual_replacement_hold (run_id, restored, original_vehicle_id),
    KEY idx_manual_replacement_retry (run_id, phase, event_id)
) ENGINE=InnoDB;
