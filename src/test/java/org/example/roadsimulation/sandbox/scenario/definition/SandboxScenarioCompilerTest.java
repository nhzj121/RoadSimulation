package org.example.roadsimulation.sandbox.scenario.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.baseline.LoadedSandboxBaseline;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselineLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.util.List;

import static org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioDefinitionV1.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxScenarioCompilerTest {
    private static final ClassPathResource BASELINE = new ClassPathResource(
            "sandbox/baseline/baseline-v1.json");
    private static final ClassPathResource DEFAULT_SCENARIO = new ClassPathResource(
            "sandbox/scenarios/default-all-eligible-v1.json");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private SandboxScenarioCompiler compiler;
    private LoadedSandboxBaseline baseline;
    private SandboxScenarioDefinitionV1 definition;

    @BeforeEach
    void setUp() {
        compiler = new SandboxScenarioCompiler(objectMapper);
        baseline = new SandboxBaselineLoader(objectMapper).load(BASELINE);
        definition = compiler.readDefinition(DEFAULT_SCENARIO);
    }

    @Test
    void compilesFormalDefaultScenarioWithPhaseOneProjectionHash() {
        CompiledSandboxScenario first = compiler.compile(baseline, definition);
        CompiledSandboxScenario second = compiler.compile(baseline, definition);

        assertEquals(2602, first.effectiveData().data().pois().size());
        assertEquals(10, first.effectiveData().data().goods().size());
        assertEquals(85, first.effectiveData().data().vehicles().size());
        assertEquals(4, first.effectiveData().data().processingChains().size());
        assertEquals(255, first.effectiveData().data().drivers().size());
        assertEquals(255, first.effectiveData().data().driverVehicleBindings().size());
        assertEquals("8f208b56e1350f26af46bc666c36c7bc1c65209d6b0c1b155bf9036e1d922922",
                first.effectiveData().baseDataProjectionSha256());
        assertEquals(first.scenarioDefinitionSha256(), second.scenarioDefinitionSha256());
        assertEquals(first.effectiveData().effectiveScenarioDataSha256(),
                second.effectiveData().effectiveScenarioDataSha256());
    }

    @Test
    void descriptiveMetadataDoesNotChangeDefinitionHashButDataOverrideDoes() {
        CompiledSandboxScenario original = compiler.compile(baseline, definition);
        SandboxScenarioDefinitionV1 renamed = new SandboxScenarioDefinitionV1(
                definition.artifactVersion(), definition.scenarioKey(), "另一个显示名称", "另一个说明",
                definition.baseline(), definition.selection(), definition.overrides());
        assertEquals(original.scenarioDefinitionSha256(),
                compiler.compile(baseline, renamed).scenarioDefinitionSha256());

        long stageId = baseline.baseline().data().processingChains().get(0).stages().get(0).id();
        Overrides changed = new Overrides(
                List.of(new StageOutputRatio(stageId, new BigDecimal("0.950000"))),
                List.of(), List.of(), List.of());
        CompiledSandboxScenario overridden = compiler.compile(
                baseline, replace(definition.selection(), changed));
        assertTrue(!original.scenarioDefinitionSha256().equals(overridden.scenarioDefinitionSha256()));
        assertEquals(original.effectiveData().baseDataProjectionSha256(),
                overridden.effectiveData().baseDataProjectionSha256());
        assertTrue(!original.effectiveData().effectiveScenarioDataSha256().equals(
                overridden.effectiveData().effectiveScenarioDataSha256()));
    }

    @Test
    void supportsExplicitTransportOnlyScenarioAndFixedPoi() {
        CompiledSandboxScenario all = compiler.compile(baseline, definition);
        long vehicleId = all.effectiveData().data().vehicles().get(0).id();
        long goodsId = all.effectiveData().data().goods().get(0).id();
        long poiId = all.effectiveData().data().pois().stream()
                .filter(poi -> "WAREHOUSE".equals(poi.poiType())).findFirst().orElseThrow().id();

        Selection selected = new Selection(
                new VehicleSelection(SelectionMode.EXPLICIT_IDS, List.of(vehicleId), List.of(),
                        null, null, null, null, List.of(), List.of()),
                new PoiSelection(SelectionMode.EXPLICIT_IDS, List.of(poiId), List.of(), List.of()),
                new GoodsSelection(SelectionMode.EXPLICIT_IDS, List.of(goodsId), List.of(), List.of()),
                new IdSelection(SelectionMode.EXPLICIT_IDS, List.of(), List.of()));
        Overrides overrides = new Overrides(List.of(), List.of(),
                List.of(new InitialInventory(poiId, goodsId, 7)),
                List.of(new VehicleInitialPoi(vehicleId, poiId, VehicleInitialPoi.FIXED_POI)));

        CompiledSandboxScenario compiled = compiler.compile(baseline, replace(selected, overrides));
        assertTrue(compiled.effectiveData().data().processingChains().isEmpty());
        assertEquals(7, compiled.effectiveData().data().initialInventories().get(0).quantity());
        assertEquals(poiId, compiled.effectiveData().fixedVehiclePois().get(vehicleId));
    }

    @Test
    void rejectsIneligibleIdInsteadOfSilentlyAddingIt() {
        Selection selected = new Selection(
                definition.selection().vehicles(), definition.selection().pois(),
                new GoodsSelection(SelectionMode.EXPLICIT_IDS, List.of(3L), List.of(), List.of()),
                definition.selection().processingChains());
        SandboxScenarioException error = assertThrows(
                SandboxScenarioException.class,
                () -> compiler.compile(baseline, replace(selected, definition.overrides())));
        assertEquals("UNKNOWN_OR_INELIGIBLE_ID", error.errorCode());
    }

    @Test
    void rejectsLossRatioAboveOneAndExcessPrecision() {
        long stageId = baseline.baseline().data().processingChains().get(0).stages().get(0).id();
        Overrides aboveOne = new Overrides(
                List.of(new StageOutputRatio(stageId, new BigDecimal("1.01"))),
                List.of(), List.of(), List.of());
        assertEquals("INVALID_RATIO", assertThrows(SandboxScenarioException.class,
                () -> compiler.compile(baseline, replace(definition.selection(), aboveOne))).errorCode());

        Overrides tooPrecise = new Overrides(
                List.of(new StageOutputRatio(stageId, new BigDecimal("0.1234567"))),
                List.of(), List.of(), List.of());
        assertEquals("INVALID_RATIO_PRECISION", assertThrows(SandboxScenarioException.class,
                () -> compiler.compile(baseline, replace(definition.selection(), tooPrecise))).errorCode());
    }

    @Test
    void rejectsZeroInventoryAndNonInitializationPoi() {
        CompiledSandboxScenario all = compiler.compile(baseline, definition);
        long vehicleId = all.effectiveData().data().vehicles().get(0).id();
        long goodsId = all.effectiveData().data().goods().get(0).id();
        long warehouseId = all.effectiveData().data().pois().stream()
                .filter(poi -> "WAREHOUSE".equals(poi.poiType())).findFirst().orElseThrow().id();
        long gasStationId = all.effectiveData().data().pois().stream()
                .filter(poi -> "GAS_STATION".equals(poi.poiType())).findFirst().orElseThrow().id();

        Overrides zero = new Overrides(List.of(), List.of(),
                List.of(new InitialInventory(warehouseId, goodsId, 0)), List.of());
        assertEquals("INVALID_INVENTORY", assertThrows(SandboxScenarioException.class,
                () -> compiler.compile(baseline, replace(definition.selection(), zero))).errorCode());

        Overrides wrongPoi = new Overrides(List.of(), List.of(), List.of(),
                List.of(new VehicleInitialPoi(vehicleId, gasStationId, VehicleInitialPoi.FIXED_POI)));
        assertEquals("INVALID_VEHICLE_INITIAL_POI_TYPE", assertThrows(SandboxScenarioException.class,
                () -> compiler.compile(baseline, replace(definition.selection(), wrongPoi))).errorCode());
    }

    private SandboxScenarioDefinitionV1 replace(Selection selection, Overrides overrides) {
        return new SandboxScenarioDefinitionV1(
                definition.artifactVersion(), definition.scenarioKey(), definition.displayName(),
                definition.description(), definition.baseline(), selection, overrides);
    }
}
