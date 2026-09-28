package org.example.roadsimulation.sandbox.workspace;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxWorkspaceSafetyTest {

    private final SandboxWorkspaceSafety safety = new SandboxWorkspaceSafety();

    @Test
    void acceptsOnlyTheFixedSandboxDatabaseName() {
        assertEquals("vehicle_scheduler_sandbox", safety.databaseName(
                "jdbc:mysql://localhost:3306/vehicle_scheduler_sandbox?useSSL=false"));
        assertEquals("vehicle_scheduler_sandbox", safety.databaseName(
                "jdbc:mariadb://localhost:3306/vehicle_scheduler_sandbox"));

        assertThrows(SandboxWorkspaceException.class, () -> safety.databaseName(null));
        assertThrows(SandboxWorkspaceException.class, () -> safety.databaseName("jdbc:h2:mem:test"));
    }

    @Test
    void validatesPrivilegesWithoutQueryingSourceBusinessTables() throws Exception {
        Connection connection = connectionWithPrivilegeCount(0L);
        Statement statement = connection.createStatement();

        assertDoesNotThrow(() -> safety.requireSafeTarget(
                "jdbc:mysql://localhost:3306/vehicle_scheduler_sandbox", connection));

        verify(statement, never()).executeQuery(contains("vehicle_scheduler.poi"));
    }

    @Test
    void rejectsAccountWithAnyNonSandboxPrivilege() throws Exception {
        Connection connection = connectionWithPrivilegeCount(1L);

        SandboxWorkspaceException exception = assertThrows(
                SandboxWorkspaceException.class,
                () -> safety.requireSafeTarget(
                        "jdbc:mysql://localhost:3306/vehicle_scheduler_sandbox", connection));

        assertEquals("SOURCE_DATABASE_ACCESSIBLE", exception.errorCode());
    }

    private Connection connectionWithPrivilegeCount(long privilegeCount) throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);

        ResultSet database = singleString("vehicle_scheduler_sandbox");
        ResultSet marker = singleString(SandboxWorkspaceSafety.WORKSPACE_KIND);
        ResultSet user = singleString("road_sandbox_runtime@localhost");
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("DATABASE()")) return database;
            if (sql.contains("workspace_kind")) return marker;
            if (sql.contains("CURRENT_USER()")) return user;
            throw new AssertionError("Unexpected SQL: " + sql);
        });

        PreparedStatement privileges = mock(PreparedStatement.class);
        ResultSet counts = mock(ResultSet.class);
        when(connection.prepareStatement(contains("information_schema.USER_PRIVILEGES")))
                .thenReturn(privileges);
        when(privileges.executeQuery()).thenReturn(counts);
        when(counts.next()).thenReturn(true);
        when(counts.getLong(1)).thenReturn(privilegeCount);
        return connection;
    }

    private ResultSet singleString(String value) throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true);
        when(rows.getString(1)).thenReturn(value);
        return rows;
    }
}
