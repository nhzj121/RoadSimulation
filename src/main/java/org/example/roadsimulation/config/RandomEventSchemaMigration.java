package org.example.roadsimulation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes foreign keys produced by an early development version of the event table.
 * Event history now stores immutable vehicle/assignment snapshots and must survive
 * deletion of the source entities.
 */
@Component
public class RandomEventSchemaMigration {
    private static final Logger logger = LoggerFactory.getLogger(RandomEventSchemaMigration.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_$]+");

    private final JdbcTemplate jdbcTemplate;

    public RandomEventSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void removeLegacyForeignKeys() {
        List<String> constraintNames = jdbcTemplate.queryForList("""
                SELECT CONSTRAINT_NAME
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'transport_random_event'
                  AND COLUMN_NAME IN ('vehicle_id', 'assignment_id')
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                """, String.class);
        for (String constraintName : constraintNames) {
            if (constraintName == null || !SAFE_IDENTIFIER.matcher(constraintName).matches()) {
                throw new IllegalStateException("unsafe legacy foreign-key name: " + constraintName);
            }
            jdbcTemplate.execute("ALTER TABLE transport_random_event DROP FOREIGN KEY `" + constraintName + "`");
            logger.info("Removed legacy random-event foreign key: {}", constraintName);
        }
    }
}
