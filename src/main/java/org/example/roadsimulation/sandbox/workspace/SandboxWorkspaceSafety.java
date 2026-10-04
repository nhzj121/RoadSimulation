package org.example.roadsimulation.sandbox.workspace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Destructive workspace operations are permitted only inside the explicitly provisioned schema. */
public final class SandboxWorkspaceSafety {

    public static final String DATABASE_NAME = "vehicle_scheduler_sandbox";
    public static final String SOURCE_DATABASE_NAME = "vehicle_scheduler";
    public static final String WORKSPACE_KIND = "ROAD_SIMULATION_SANDBOX";
    public static final String MARKER_TABLE = "sandbox_workspace_marker";
    public static final String SCENARIO_TABLE = "sandbox_scenario";
    public static final String SCENARIO_REVISION_TABLE = "sandbox_scenario_revision";
    public static final String RUN_SPEC_TABLE = "sandbox_run_spec";
    public static final String RUN_SPEC_REVISION_TABLE = "sandbox_run_spec_revision";
    public static final String RUN_VEHICLE_INITIAL_STATE_TABLE = "sandbox_run_vehicle_initial_state";
    public static final String LOCK_NAME = "roadsimulation:sandbox:prepare";

    private static final Pattern JDBC_DATABASE = Pattern.compile(
            "^jdbc:(?:mariadb|mysql)://[^/]+/([^?;]+).*$", Pattern.CASE_INSENSITIVE);

    public void requireSafeTarget(String jdbcUrl, Connection connection) throws SQLException {
        String databaseFromUrl = databaseName(jdbcUrl);
        require(DATABASE_NAME.equalsIgnoreCase(databaseFromUrl),
                "UNSAFE_DATABASE", "Refusing sandbox operation for JDBC database: " + databaseFromUrl);

        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT DATABASE()")) {
            require(rows.next() && DATABASE_NAME.equalsIgnoreCase(rows.getString(1)),
                    "UNSAFE_DATABASE", "Connected database is not " + DATABASE_NAME);
        }
        requireMarker(connection);
        requireSourceDatabaseInaccessible(connection);
    }

    public void acquirePreparationLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, LOCK_NAME);
            try (ResultSet rows = statement.executeQuery()) {
                require(rows.next() && rows.getInt(1) == 1,
                        "WORKSPACE_BUSY", "Another sandbox preparation owns the workspace lock");
            }
        }
    }

    public void releasePreparationLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, LOCK_NAME);
            statement.executeQuery().close();
        } catch (SQLException ignored) {
            // Connection close also releases the lock. Do not hide the original preparation failure.
        }
    }

    public void requireNoUnfinishedExecution(Connection connection) throws SQLException {
        requireNoUnfinishedExecution(connection, null);
    }

    public void requireNoUnfinishedExecution(Connection connection, String jobId) throws SQLException {
        requireManagementJobOwner(connection, jobId);
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT workspace_state FROM sandbox_workspace_marker WHERE marker_id=1")) {
            if (rows.next() && "EXECUTION_RUNNING".equals(rows.getString(1))) {
                throw new SandboxWorkspaceException("EXECUTION_STILL_RUNNING",
                        "Workspace has an unfinished execution; inspect it before preparing again");
            }
        }
    }

    /** Called under the preparation lock. Legacy commands cannot bypass a durable job reservation. */
    public void requireManagementJobOwner(Connection connection, String jobId) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sandbox_workspace_marker' AND COLUMN_NAME='active_job_id'")) {
            if (!rows.next() || rows.getInt(1) == 0) {
                if (jobId != null) throw new SandboxWorkspaceException("CONTROL_SCHEMA_NOT_READY", "Management requires control schema v7");
                return;
            }
        }
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT active_job_id FROM sandbox_workspace_marker WHERE marker_id=1")) {
            if (!rows.next()) throw new SandboxWorkspaceException("MISSING_MARKER", "Missing sandbox marker");
            String active = rows.getString(1);
            if (!java.util.Objects.equals(active, jobId)) throw new SandboxWorkspaceException("WORKSPACE_BUSY", "Workspace reserved by a management job");
        }
    }

    public void requirePreparationLockHeld(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT IS_USED_LOCK(?)=CONNECTION_ID()")) {
            statement.setString(1, LOCK_NAME);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1) throw new SandboxWorkspaceException("WORKSPACE_LOCK_REQUIRED", "Caller must own the preparation lock");
            }
        }
    }

    public void clearExecutionReference(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sandbox_workspace_marker' AND COLUMN_NAME='active_execution_id'
                """)) {
            if (columns.next() && columns.getInt(1) == 1) {
                try (Statement update = connection.createStatement()) {
                    update.executeUpdate("UPDATE sandbox_workspace_marker SET active_execution_id=NULL WHERE marker_id=1");
                }
            }
        }
    }

    public String databaseName(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new SandboxWorkspaceException("UNSAFE_DATABASE", "Sandbox JDBC URL must not be blank");
        }
        Matcher matcher = JDBC_DATABASE.matcher(jdbcUrl.trim());
        if (!matcher.matches()) {
            throw new SandboxWorkspaceException("UNSAFE_DATABASE", "Unsupported JDBC URL: " + jdbcUrl);
        }
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    private void requireMarker(Connection connection) throws SQLException {
        String sql = "SELECT workspace_kind FROM " + MARKER_TABLE + " WHERE marker_id = 1";
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            require(rows.next() && WORKSPACE_KIND.equals(rows.getString(1)),
                    "MISSING_MARKER", "Sandbox safety marker is missing or invalid");
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException(
                    "MISSING_MARKER", "Sandbox safety marker is missing or unreadable", exception);
        }
    }

    private void requireSourceDatabaseInaccessible(Connection connection) {
        try {
            String grantee;
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT CURRENT_USER()")) {
                require(rows.next(), "SOURCE_PRIVILEGE_CHECK_FAILED", "Cannot resolve current database account");
                String currentUser = rows.getString(1);
                int separator = currentUser == null ? -1 : currentUser.lastIndexOf('@');
                require(separator > 0 && separator < currentUser.length() - 1,
                        "SOURCE_PRIVILEGE_CHECK_FAILED", "Cannot normalize current database account");
                grantee = "'" + currentUser.substring(0, separator).replace("'", "''")
                        + "'@'" + currentUser.substring(separator + 1).replace("'", "''") + "'";
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT
                      (SELECT COUNT(*) FROM information_schema.USER_PRIVILEGES
                       WHERE GRANTEE=? AND PRIVILEGE_TYPE<>'USAGE')
                      +
                      (SELECT COUNT(*) FROM information_schema.SCHEMA_PRIVILEGES
                       WHERE GRANTEE=? AND TABLE_SCHEMA=?)
                      +
                      (SELECT COUNT(*) FROM information_schema.TABLE_PRIVILEGES
                       WHERE GRANTEE=? AND TABLE_SCHEMA=?)
                    """)) {
                statement.setString(1, grantee);
                statement.setString(2, grantee);
                statement.setString(3, SOURCE_DATABASE_NAME);
                statement.setString(4, grantee);
                statement.setString(5, SOURCE_DATABASE_NAME);
                try (ResultSet rows = statement.executeQuery()) {
                    require(rows.next() && rows.getLong(1) == 0L,
                            "SOURCE_DATABASE_ACCESSIBLE",
                            "Sandbox runtime account has privileges outside the dedicated sandbox schema");
                }
            }
        } catch (SandboxWorkspaceException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new SandboxWorkspaceException(
                    "SOURCE_PRIVILEGE_CHECK_FAILED",
                    "Cannot verify sandbox runtime account privileges without reading the source database",
                    exception);
        }
    }

    private void require(boolean condition, String code, String message) {
        if (!condition) {
            throw new SandboxWorkspaceException(code, message);
        }
    }
}
