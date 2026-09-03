package org.example.roadsimulation.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RandomEventSchemaMigrationTest {

    @Test
    void removesOnlyLegacyEventForeignKeysReturnedByMetadata() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("FK_event_vehicle", "FK_event_assignment"));
        RandomEventSchemaMigration migration = new RandomEventSchemaMigration(jdbcTemplate);

        migration.removeLegacyForeignKeys();

        verify(jdbcTemplate).execute(
                "ALTER TABLE transport_random_event DROP FOREIGN KEY `FK_event_vehicle`"
        );
        verify(jdbcTemplate).execute(
                "ALTER TABLE transport_random_event DROP FOREIGN KEY `FK_event_assignment`"
        );
    }
}
