package org.example.roadsimulation.sandbox.workspace;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Clearing active-run metadata never deletes immutable published revisions. */
final class SandboxRunMarkerMaintenance {
    private SandboxRunMarkerMaintenance() {}
    static void clear(Connection connection) throws SQLException {
        Set<String> present=new HashSet<>();
        try(var statement=connection.createStatement();var rows=statement.executeQuery("""
                SELECT COLUMN_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sandbox_workspace_marker'
                """)) {while(rows.next())present.add(rows.getString(1));}
        var fields=List.of("run_spec_key","run_spec_revision","run_specification_sha256","random_protocol_id",
                "root_seed_fingerprint","resolved_vehicle_initial_state_sha256","prepared_run_facts_sha256",
                "run_artifact_version","weather_timeline_sha256","event_configuration_sha256","failure_phase");
        var assignments=fields.stream().filter(present::contains).map(name->name+"=NULL").toList();
        if(!assignments.isEmpty())try(var statement=connection.createStatement()) {
            statement.executeUpdate("UPDATE sandbox_workspace_marker SET "+String.join(",",assignments)+" WHERE marker_id=1");
        }
    }
}
