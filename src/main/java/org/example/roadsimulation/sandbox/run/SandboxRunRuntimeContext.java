package org.example.roadsimulation.sandbox.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.roadsimulation.config.DemandGenerationMode;
import org.example.roadsimulation.config.DispatchStrategy;
import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.sandbox.random.SandboxRandomDomain;
import org.example.roadsimulation.sandbox.random.SandboxRandomProtocol;
import org.example.roadsimulation.sandbox.random.SplitMix64Random;
import org.example.roadsimulation.sandbox.random.SplitMix64V1;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceSafety;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Immutable runtime view of the currently prepared sandbox run revision.
 *
 * <p>The component exists only under {@code sandbox-runtime}. It reads only
 * the sandbox control tables and refuses to start unless the marker, revision,
 * hashes and materialized vehicle states all agree.</p>
 */
@Component
@Profile("sandbox-runtime")
public final class SandboxRunRuntimeContext {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final SimulationRuntimeConfig runtimeConfig;
    private final SimulationContext simulationContext;
    private final boolean startupPreGenerationEnabled;
    private final SandboxRandomProtocol randomProtocol;
    private final SandboxRunCodec runCodec;

    private volatile SandboxRunSpecificationRevisionV1 revision;
    private volatile SandboxRunSpecificationRevisionV2 revisionV2;
    private volatile String deterministicSimulationRunId;
    @Value("${sandbox.baseline-resource:classpath:sandbox/baseline/baseline-v1.json}")
    private org.springframework.core.io.Resource baselineResource =
            new org.springframework.core.io.ClassPathResource("sandbox/baseline/baseline-v1.json");

    public SandboxRunRuntimeContext(
            DataSource dataSource,
            ObjectMapper objectMapper,
            SimulationRuntimeConfig runtimeConfig,
            SimulationContext simulationContext,
            @Value("${app.simulation.startup-pre-generation.enabled:false}")
            boolean startupPreGenerationEnabled
    ) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.runtimeConfig = runtimeConfig;
        this.simulationContext = simulationContext;
        this.startupPreGenerationEnabled = startupPreGenerationEnabled;
        this.randomProtocol = new SandboxRandomProtocol(objectMapper);
        this.runCodec = new SandboxRunCodec(objectMapper);
    }

    @PostConstruct
    public void loadAndFreeze() {
        if (startupPreGenerationEnabled) {
            throw new SandboxWorkspaceException(
                    "SANDBOX_STARTUP_PREGENERATION_FORBIDDEN",
                    "Sandbox runtime requires startup pre-generation to remain disabled");
        }
        try (Connection connection = dataSource.getConnection()) {
            String database = connection.getCatalog();
            if (!SandboxWorkspaceSafety.DATABASE_NAME.equals(database)) {
                throw new SandboxWorkspaceException(
                        "UNSAFE_DATABASE", "Sandbox runtime must use vehicle_scheduler_sandbox");
            }
            Marker marker = readMarker(connection);
            if (loadV2IfPresent(connection, marker)) return;
            throw new SandboxWorkspaceException("SANDBOX_V1_READ_ONLY",
                    "Published v1 is available for export/verification only; publish v2 before a new formal run");
        } catch (SandboxWorkspaceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SandboxWorkspaceException(
                    "SANDBOX_RUN_CONTEXT_LOAD_FAILED",
                    "Cannot load the prepared sandbox run specification", exception);
        }
    }

    public SandboxRunSpecificationRevisionV1 revision() {
        return requireLoaded();
    }

    public SandboxRunSpecificationV1 specification() {
        return requireLoaded().specification();
    }

    public SandboxAlgorithmProfile algorithmProfile() {
        return revisionV2==null?requireLoaded().algorithmProfile():revisionV2.algorithmProfile();
    }

    public String deterministicSimulationRunId() {
        requireAnyLoaded();
        return deterministicSimulationRunId;
    }

    public long deriveSeed(SandboxRandomDomain domain, Map<String, ?> stableBusinessKey) {
        return randomProtocol.deriveSeed(
                randomProtocolSpecification().rootSeed(), domain, stableBusinessKey);
    }

    public String deriveSeedHex(SandboxRandomDomain domain, Map<String, ?> stableBusinessKey) {
        return randomProtocol.deriveSeedHex(
                randomProtocolSpecification().rootSeed(), domain, stableBusinessKey);
    }

    public SplitMix64V1 random(SandboxRandomDomain domain, Map<String, ?> stableBusinessKey) {
        return randomProtocol.random(
                randomProtocolSpecification().rootSeed(), domain, stableBusinessKey);
    }

    public Random javaRandom(SandboxRandomDomain domain, Map<String, ?> stableBusinessKey) {
        return new SplitMix64Random(deriveSeed(domain, stableBusinessKey));
    }

    public long environmentPhaseSeed() {
        if(isV2())throw new SandboxWorkspaceException("PERIODIC_ENVIRONMENT_DISABLED","v2 uses frozen event weather, not the legacy periodic environment");
        return deriveSeed(
                SandboxRandomDomain.ENVIRONMENT_PHASE,
                Map.of("environmentScenarioId", specification().environment().scenarioId()));
    }

    public Map<Long, SandboxVehicleInitialState> vehicleInitialStatesByVehicleId() {
        LinkedHashMap<Long, SandboxVehicleInitialState> result = new LinkedHashMap<>();
        (revisionV2==null?requireLoaded().vehicleInitialStates():revisionV2.vehicleInitialStates()).stream()
                .sorted(Comparator.comparingLong(SandboxVehicleInitialState::vehicleId))
                .forEach(state -> result.put(state.vehicleId(), state));
        return Map.copyOf(result);
    }

    private void validate(
            Marker marker,
            SandboxRunSpecificationRevisionV1 loaded,
            List<SandboxVehicleInitialState> storedStates
    ) {
        if (!(SandboxRunSpecificationStore.CONTROL_SCHEMA_VERSION.equals(marker.controlSchemaVersion())
                || SandboxRunSpecificationStore.supportsV2ControlSchema(marker.controlSchemaVersion()))
                || !"RUN_SPEC_READY".equals(marker.workspaceState())
                || !SandboxRandomProtocol.PROTOCOL_ID.equals(marker.randomProtocolId())
                || !loaded.runSpecKey().equals(marker.runSpecKey())
                || loaded.revision() != marker.runSpecRevision()
                || !loaded.fingerprints().runSpecificationSha256().equals(marker.runSpecificationSha256())
                || !loaded.fingerprints().resolvedVehicleInitialStateSha256().equals(
                marker.resolvedVehicleInitialStateSha256())
                || !loaded.fingerprints().preparedRunFactsSha256().equals(marker.preparedRunFactsSha256())
                || !randomProtocol.rootSeedFingerprint(loaded.specification().random().rootSeed()).equals(
                marker.rootSeedFingerprint())) {
            throw new SandboxWorkspaceException(
                    "SANDBOX_RUN_MARKER_MISMATCH",
                    "Sandbox marker does not match the published run revision");
        }
        String specificationHash = runCodec.runSpecificationHash(
                loaded.specification(), loaded.algorithmProfile());
        String stateHash = runCodec.vehicleInitialStateHash(loaded.vehicleInitialStates());
        String preparedHash = runCodec.preparedRunFactsHash(
                loaded.specification().scenario().effectiveScenarioDataSha256(),
                specificationHash,
                stateHash);
        if (!specificationHash.equals(loaded.fingerprints().runSpecificationSha256())
                || !stateHash.equals(loaded.fingerprints().resolvedVehicleInitialStateSha256())
                || !preparedHash.equals(loaded.fingerprints().preparedRunFactsSha256())
                || !loaded.vehicleInitialStates().equals(storedStates)) {
            throw new SandboxWorkspaceException(
                    "SANDBOX_RUN_REVISION_MISMATCH",
                    "Published run revision or stored vehicle initial states failed verification");
        }
        if (!"PRODUCTION".equals(loaded.specification().demand().mode())
                || loaded.specification().demand().startupPreGenerationEnabled()) {
            throw new SandboxWorkspaceException(
                    "SANDBOX_UNSUPPORTED_DEMAND_MODE",
                    "Sandbox runtime accepts only PRODUCTION without startup pre-generation");
        }
    }

    private Marker readMarker(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT control_schema_version,workspace_state,run_spec_key,run_spec_revision,
                       run_specification_sha256,random_protocol_id,root_seed_fingerprint,
                       resolved_vehicle_initial_state_sha256,prepared_run_facts_sha256
                FROM sandbox_workspace_marker
                WHERE marker_id=1 AND workspace_kind='ROAD_SIMULATION_SANDBOX'
                """); ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new SandboxWorkspaceException(
                        "MISSING_MARKER", "Sandbox workspace marker is missing");
            }
            return new Marker(
                    rows.getString(1), rows.getString(2), rows.getString(3), rows.getInt(4),
                    rows.getString(5), rows.getString(6), rows.getString(7),
                    rows.getString(8), rows.getString(9));
        }
    }

    private SandboxRunSpecificationRevisionV1 readRevision(
            Connection connection,
            String runSpecKey,
            int revisionNumber
    ) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT revision_json FROM sandbox_run_spec_revision
                WHERE run_spec_key=? AND revision_no=? AND archived=b'0'
                """)) {
            statement.setString(1, runSpecKey);
            statement.setInt(2, revisionNumber);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SandboxWorkspaceException(
                            "RUN_SPEC_REVISION_NOT_FOUND",
                            "Active sandbox run specification revision does not exist");
                }
                return objectMapper.readValue(rows.getString(1), SandboxRunSpecificationRevisionV1.class);
            }
        }
    }

    private List<SandboxVehicleInitialState> readInitialStates(
            Connection connection,
            String runSpecKey,
            int revisionNumber
    ) throws Exception {
        List<SandboxVehicleInitialState> states = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT vehicle_id,initialization_policy,poi_id,decision_domain,decision_key,derived_seed_hex
                FROM sandbox_run_vehicle_initial_state
                WHERE run_spec_key=? AND revision_no=? ORDER BY vehicle_id
                """)) {
            statement.setString(1, runSpecKey);
            statement.setInt(2, revisionNumber);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    states.add(new SandboxVehicleInitialState(
                            rows.getLong(1), rows.getString(2), rows.getLong(3),
                            rows.getString(4), rows.getString(5), rows.getString(6)));
                }
            }
        }
        return List.copyOf(states);
    }

    private SandboxRunSpecificationRevisionV1 requireLoaded() {
        if(isV2())throw new SandboxWorkspaceException("V1_RUNTIME_VIEW_FORBIDDEN","Use version-neutral accessors for v2; no synthetic v1 environment is available");
        SandboxRunSpecificationRevisionV1 current = revision;
        if (current == null) {
            throw new IllegalStateException("Sandbox run context has not been loaded");
        }
        return current;
    }


    public boolean isV2() {return revisionV2!=null;}
    public SandboxRunSpecificationRevisionV2 revisionV2() {
        if(revisionV2==null)throw new SandboxWorkspaceException("RUN_SPEC_V2_REQUIRED","No prepared v2 revision");
        return revisionV2;
    }
    public SandboxRunSpecificationV1.SimulationClock simulationClock() {
        return isV2()?revisionV2.specification().simulationClock():specification().simulationClock();
    }
    public SandboxRunSpecificationV1.Dispatch dispatch() {
        return isV2()?revisionV2.specification().dispatch():specification().dispatch();
    }
    public SandboxRunSpecificationV1.DriverBehavior driverBehavior() {
        return isV2()?revisionV2.specification().driverBehavior():specification().driverBehavior();
    }
    public SandboxRunSpecificationV1.RandomProtocol randomProtocolSpecification() {
        return isV2()?revisionV2.specification().random():specification().random();
    }
    private void requireAnyLoaded() {if(!isV2())requireLoaded();}

    private boolean loadV2IfPresent(Connection connection,Marker marker) throws Exception {
        String encoded;
        try(var statement=connection.prepareStatement(
                "SELECT revision_json FROM sandbox_run_spec_revision WHERE run_spec_key=? AND revision_no=? AND archived=b'0'")) {
            statement.setString(1,marker.runSpecKey());statement.setInt(2,marker.runSpecRevision());
            try(var rows=statement.executeQuery()) {
                if(!rows.next())throw new SandboxWorkspaceException("RUN_SPEC_REVISION_NOT_FOUND","Active revision missing");
                encoded=rows.getString(1);
            }
        }
        if(!SandboxRunSpecificationRevisionV2.ARTIFACT_VERSION.equals(objectMapper.readTree(encoded).path("artifactVersion").asText())) return false;
        if(!SandboxRunSpecificationStore.supportsV2ControlSchema(marker.controlSchemaVersion()))
            throw new SandboxWorkspaceException("CONTROL_SCHEMA_NOT_READY","v2 requires control schema v4");
        var loaded=objectMapper.readValue(encoded,SandboxRunSpecificationRevisionV2.class);
        var reference=loaded.specification().scenario();
        org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1 scene;
        try(var statement=connection.prepareStatement(
                "SELECT revision_json FROM sandbox_scenario_revision WHERE scenario_key=? AND revision_no=? AND archived=b'0'")) {
            statement.setString(1,reference.scenarioKey());statement.setInt(2,reference.revision());
            try(var rows=statement.executeQuery()) {
                if(!rows.next())throw new SandboxWorkspaceException("SCENARIO_REVISION_NOT_FOUND","Published scenario missing");
                scene=objectMapper.readValue(rows.getString(1),
                        org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1.class);
            }
        }
        var baseline=new org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader(objectMapper).load(baselineResource);
        var scenario=new org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioCompiler(objectMapper).compile(baseline,scene.definition());
        var compiled=new SandboxRunCompilerV2(objectMapper).compile(loaded.specification(),scene,scenario);
        if(!"RUN_SPEC_READY".equals(marker.workspaceState()) || !loaded.runSpecKey().equals(marker.runSpecKey())
                || loaded.revision()!=marker.runSpecRevision() || !SandboxRandomProtocol.PROTOCOL_ID.equals(marker.randomProtocolId())
                || !compiled.algorithmProfile().equals(loaded.algorithmProfile())
                || !compiled.vehicleInitialStates().equals(loaded.vehicleInitialStates())
                || !compiled.weatherTimeline().equals(loaded.weatherTimeline())
                || !compiled.eventConfiguration().equals(loaded.eventConfiguration())
                || !compiled.runSpecificationSha256().equals(loaded.fingerprints().runSpecificationSha256())
                || !compiled.resolvedVehicleInitialStateSha256().equals(loaded.fingerprints().resolvedVehicleInitialStateSha256())
                || !compiled.preparedRunFactsSha256().equals(loaded.fingerprints().preparedRunFactsSha256())
                || !compiled.weatherTimelineSha256().equals(loaded.fingerprints().weatherTimelineSha256())
                || !compiled.eventConfigurationSha256().equals(loaded.fingerprints().eventConfigurationSha256())
                || !compiled.runSpecificationSha256().equals(marker.runSpecificationSha256())
                || !compiled.resolvedVehicleInitialStateSha256().equals(marker.resolvedVehicleInitialStateSha256())
                || !compiled.preparedRunFactsSha256().equals(marker.preparedRunFactsSha256())
                || !randomProtocol.rootSeedFingerprint(loaded.specification().random().rootSeed()).equals(marker.rootSeedFingerprint())
                || !compiled.vehicleInitialStates().equals(readInitialStates(connection,marker.runSpecKey(),marker.runSpecRevision())))
            throw new SandboxWorkspaceException("SANDBOX_RUN_REVISION_MISMATCH","v2 prepared facts do not match the published revision");
        try(var statement=connection.createStatement();var rows=statement.executeQuery(
                "SELECT run_artifact_version,weather_timeline_sha256,event_configuration_sha256 FROM sandbox_workspace_marker WHERE marker_id=1")) {
            if(!rows.next() || !SandboxRunSpecificationV2.ARTIFACT_VERSION.equals(rows.getString(1))
                    || !compiled.weatherTimelineSha256().equals(rows.getString(2))
                    || !compiled.eventConfigurationSha256().equals(rows.getString(3)))
                throw new SandboxWorkspaceException("SANDBOX_RUN_MARKER_MISMATCH","v2 weather/event marker mismatch");
        }
        runtimeConfig.freezeForSandbox(DispatchStrategy.valueOf(loaded.specification().dispatch().strategy()),
                DemandGenerationMode.PRODUCTION,loaded.specification().random().rootSeed(),loaded.algorithmProfile());
        simulationContext.configureDeterministicRun(compiled.deterministicSimulationRunId(),
                loaded.specification().simulationClock().startLocalDateTime(),loaded.specification().simulationClock().tickDurationSeconds());
        revisionV2=loaded;deterministicSimulationRunId=compiled.deterministicSimulationRunId();
        return true;
    }

    private record Marker(
            String controlSchemaVersion,
            String workspaceState,
            String runSpecKey,
            int runSpecRevision,
            String runSpecificationSha256,
            String randomProtocolId,
            String rootSeedFingerprint,
            String resolvedVehicleInitialStateSha256,
            String preparedRunFactsSha256
    ) {}
}
