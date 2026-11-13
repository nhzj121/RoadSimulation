package org.example.roadsimulation.sandbox.management;

import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceSafety;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.sql.Connection;
import java.sql.SQLException;

/** Ordinary runs hold the same workspace lock that a sandbox worker or submitter must acquire. */
public final class OrdinaryRunAdmission implements AutoCloseable {
    public enum Owner { SIMULATION, COMPARISON }

    @ResponseStatus(HttpStatus.CONFLICT)
    static final class AdmissionDenied extends IllegalStateException {
        AdmissionDenied(String reason) { super(reason); }
    }

    @FunctionalInterface
    interface ConnectionSource {
        Connection open() throws SQLException;
    }

    private final ConnectionSource connections;
    private final SandboxManagementService management;
    private final SandboxWorkspaceSafety safety = new SandboxWorkspaceSafety();
    private Connection connection;
    private Owner owner;

    OrdinaryRunAdmission(ConnectionSource connections, SandboxManagementService management) {
        this.connections = connections;
        this.management = management;
    }

    public synchronized void acquire(Owner candidate) {
        if (owner == candidate) {
            requireOwnership(candidate);
            return;
        }
        if (owner != null) throw new AdmissionDenied("普通仿真工作区已有活动运行");

        Connection pending = null;
        try {
            pending = connections.open();
            safety.acquirePreparationLock(pending);
            var observation = management.navigationObservation();
            if (observation.inspectionRequired())
                throw new AdmissionDenied("上一次沙箱任务已中断，待检查收尾");
            if (observation.busy())
                throw new SandboxWorkspaceException("WORKSPACE_BUSY", "Sandbox execution has not released admission");
            connection = pending;
            owner = candidate;
            pending = null;
        } catch (SandboxWorkspaceException failure) {
            throw new AdmissionDenied("WORKSPACE_BUSY".equals(failure.errorCode())
                    ? "数据沙箱正在准备、运行或收尾" : "无法确认数据沙箱运行状态");
        } catch (SQLException failure) {
            throw new AdmissionDenied("无法确认数据沙箱运行状态");
        } finally {
            closeConnection(pending);
        }
    }

    public synchronized void requireOwnership(Owner candidate) {
        if (owner == candidate) {
            try (var check = connection.prepareStatement("SELECT IS_USED_LOCK(?) = CONNECTION_ID()")) {
                check.setString(1, SandboxWorkspaceSafety.LOCK_NAME);
                try (var result = check.executeQuery()) {
                    if (result.next() && result.getBoolean(1)) return;
                }
            } catch (SQLException ignored) {
                // A broken connection cannot authorize another tick.
            }
        }
        throw new AdmissionDenied("普通仿真运行许可已失效，请停止后重新启动");
    }

    public synchronized void release(Owner candidate) {
        if (owner == candidate) close();
    }

    @Override
    public synchronized void close() {
        closeConnection(connection);
        connection = null;
        owner = null;
    }

    private void closeConnection(Connection value) {
        if (value == null) return;
        try { value.close(); }
        catch (SQLException ignored) { /* Connection loss also releases the database lock. */ }
    }
}
