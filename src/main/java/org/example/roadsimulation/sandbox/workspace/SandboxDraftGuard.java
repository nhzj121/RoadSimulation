package org.example.roadsimulation.sandbox.workspace;

import java.sql.Connection;
import java.sql.SQLException;

/** Optimistic control-plane writes. Tables and columns are selected only by server code. */
public final class SandboxDraftGuard {
    private SandboxDraftGuard() {}

    public record Saved<T>(long rowVersion, T compilation) {}

    public static long save(Connection connection, boolean scenario, String key, String name,
                            String description, String json, long expected) throws SQLException {
        if (expected < -1) throw new SandboxWorkspaceException("INVALID_DRAFT_GUARD", "Invalid draft version");
        String table = scenario ? "sandbox_scenario" : "sandbox_run_spec";
        String column = scenario ? "scenario_key" : "run_spec_key";
        String sql = expected == -1
                ? "INSERT INTO " + table + "(" + column + ",display_name,description,draft_json,draft_updated_at,row_version,archived) VALUES(?,?,?,?,CURRENT_TIMESTAMP(6),0,b'0')"
                : "UPDATE " + table + " SET display_name=?,description=?,draft_json=?,draft_updated_at=CURRENT_TIMESTAMP(6),row_version=row_version+1 WHERE " + column + "=? AND row_version=? AND archived=b'0'";
        try (var statement = connection.prepareStatement(sql)) {
            if (expected == -1) {
                statement.setString(1, key); statement.setString(2, name);
                statement.setString(3, description); statement.setString(4, json);
            } else {
                statement.setString(1, name); statement.setString(2, description);
                statement.setString(3, json); statement.setString(4, key); statement.setLong(5, expected);
            }
            if (statement.executeUpdate() != 1) conflict();
        } catch (SQLException failure) {
            if (failure.getErrorCode() == 1062) conflict();
            throw failure;
        }
        return expected + 1;
    }

    /** Call while holding the draft's FOR UPDATE lock, before creating OR reusing a revision. */
    public static void publishing(Long expectedVersion, String expectedHash, long actualVersion, String actualHash) {
        if (expectedVersion == null) return; // Existing internal CLI contracts remain unchanged.
        if (expectedVersion < 0 || expectedHash == null || !expectedHash.matches("[0-9a-f]{64}"))
            throw new SandboxWorkspaceException("INVALID_DRAFT_GUARD", "Invalid publish guard");
        if (expectedVersion != actualVersion || !expectedHash.equals(actualHash)) conflict();
    }

    private static void conflict() {
        throw new SandboxWorkspaceException("DRAFT_CONFLICT", "Draft changed; reload before saving or publishing");
    }
}
