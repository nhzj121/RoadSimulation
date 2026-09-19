package org.example.roadsimulation.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RandomEventSchemaMigrationTest {

    @Test void plannedEndMappingKeepsMicrosecondPrecision() throws Exception {
        var column=org.example.roadsimulation.entity.TransportRandomEvent.class.getDeclaredField("plannedEndTime")
                .getAnnotation(jakarta.persistence.Column.class);
        org.junit.jupiter.api.Assertions.assertEquals("DATETIME(6)",column.columnDefinition());
        org.junit.jupiter.api.Assertions.assertTrue(column.nullable());
    }

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
    @Test void makesPlannedEndNullableOnlyWhenLegacyColumnIsNotNullable() {
        JdbcTemplate jdbcTemplate=mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("KEY_COLUMN_USAGE"),eq(String.class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("IS_NULLABLE"),eq(String.class))).thenReturn("NO");
        new RandomEventSchemaMigration(jdbcTemplate).removeLegacyForeignKeys();
        verify(jdbcTemplate).execute("ALTER TABLE transport_random_event MODIFY planned_end_time DATETIME(6) NULL");
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(),eq(String.class))).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(contains("IS_NULLABLE"),eq(String.class))).thenReturn("YES");
        new RandomEventSchemaMigration(jdbcTemplate).removeLegacyForeignKeys();
        verify(jdbcTemplate,never()).execute(contains("planned_end_time"));
    }
}
