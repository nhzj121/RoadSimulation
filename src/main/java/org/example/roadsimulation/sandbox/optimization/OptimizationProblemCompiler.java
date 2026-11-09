package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.evaluation.EvaluationMetricCatalog;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.sandbox.baseline.*;
import org.example.roadsimulation.sandbox.scenario.definition.*;
import org.example.roadsimulation.sandbox.run.*;
import org.example.roadsimulation.sandbox.random.*;
import org.example.roadsimulation.service.ProcessingChainGraphValidator;
import org.example.roadsimulation.service.production.ProductionRules;
import org.springframework.core.io.ByteArrayResource;
import java.math.BigDecimal;
import java.util.*;
import static org.example.roadsimulation.sandbox.optimization.ProductionTaskCatalog.*;

/** Pure compilation from re-authenticated frozen inputs; does not prepare or execute a workspace. */
public final class OptimizationProblemCompiler {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public CompiledOptimizationProblem compile(LoadedSandboxBaseline baseline, SandboxScenarioRevisionV1 revision,
            CompiledSandboxScenario suppliedScenario, CompiledSandboxRunSpecificationV2 suppliedRun,
            SphericalPathModel.Configuration pathConfiguration, String evaluationContractVersion) {
        Objects.requireNonNull(pathConfiguration, "pathConfiguration");
        if (!EvaluationMetricCatalog.CONTRACT_VERSION.equals(evaluationContractVersion))
            throw new IllegalArgumentException("Unsupported evaluation contract version");
        var authenticatedBaseline = authenticate(baseline);
        var scenario = new SandboxScenarioCompiler(json).compile(authenticatedBaseline, revision.definition());
        var codec = new SandboxScenarioCodec(json);
        String dataHash = scenario.effectiveData().effectiveScenarioDataSha256();
        if (!scenario.scenarioDefinitionSha256().equals(revision.fingerprints().scenarioDefinitionSha256())
                || !dataHash.equals(revision.fingerprints().effectiveScenarioDataSha256())
                || !scenario.effectiveData().baseDataProjectionSha256().equals(revision.fingerprints().baseDataProjectionSha256())
                || !SandboxScenarioCodec.CANONICALIZATION.equals(revision.fingerprints().canonicalization())
                || !SandboxScenarioRevisionV1.ARTIFACT_VERSION.equals(revision.artifactVersion())
                || !scenario.resolvedSelection().equals(revision.resolvedSelection())
                || !scenario.resolvedSelection().equals(suppliedScenario.resolvedSelection())
                || !scenario.effectiveData().baseDataProjectionSha256().equals(suppliedScenario.effectiveData().baseDataProjectionSha256())
                || !codec.definitionHash(suppliedScenario.normalizedDefinition(), suppliedScenario.resolvedSelection())
                        .equals(scenario.scenarioDefinitionSha256())
                || !codec.effectiveDataHash(suppliedScenario.effectiveData().data(), suppliedScenario.effectiveData().vehicleInitializations()).equals(dataHash)
                || !dataHash.equals(suppliedScenario.effectiveData().effectiveScenarioDataSha256())
                || !scenario.scenarioDefinitionSha256().equals(suppliedScenario.scenarioDefinitionSha256()))
            throw new IllegalArgumentException("Frozen scenario evidence mismatch");
        var run = new SandboxRunCompilerV2(json).compile(suppliedRun.normalizedSpecification(), revision, scenario);
        if (!json.valueToTree(run).equals(json.valueToTree(suppliedRun)))
            throw new IllegalArgumentException("Frozen run evidence mismatch");

        var spec = run.normalizedSpecification(); var clock = spec.simulationClock();
        var facts = new ProductionFactsAdapter(scenario.effectiveData().data());
        var executionPayload = (ObjectNode) json.valueToTree(spec);
        executionPayload.remove(List.of("scenario", "runSpecKey", "displayName", "description"));
        executionPayload.set("vehicleInitialStates", json.valueToTree(run.vehicleInitialStates()));
        executionPayload.set("weatherTimeline", json.valueToTree(run.weatherTimeline()));
        executionPayload.set("eventConfiguration", json.valueToTree(run.eventConfiguration()));
        executionPayload.set("algorithmProfile", json.valueToTree(run.algorithmProfile()));
        executionPayload.set("pathConfiguration", json.valueToTree(pathConfiguration));
        var input = new OptimizationInputManifest(OptimizationInputManifest.VERSION, ProductionRules.VERSION,
                OptimizationJson.fingerprint(businessFacts(json.valueToTree(scenario.effectiveData().data()))),
                OptimizationJson.fingerprint(executionPayload), evaluationContractVersion,
                clock.startLocalDateTime(), clock.totalLoops(), clock.tickDurationSeconds());
        var catalog = catalog(input, facts, spec.random().rootSeed());
        var dependencies = ProductionDependencies.from(catalog);
        var endpoints = new ArrayList<SphericalPathModel.Endpoint>();
        facts.pois.forEach((id, p) -> endpoints.add(new SphericalPathModel.Endpoint("POI:" + id, p.getLongitude(), p.getLatitude())));
        for (var state : run.vehicleInitialStates()) {
            POI poi = facts.pois.get(state.poiId());
            if (poi == null) throw new IllegalArgumentException("Missing vehicle initial POI");
            endpoints.add(new SphericalPathModel.Endpoint("VEHICLE_START:" + state.vehicleId(), poi.getLongitude(), poi.getLatitude()));
        }
        var paths = new SphericalPathModel(SphericalPathModel.VERSION, pathConfiguration, endpoints);
        return new CompiledOptimizationProblem(CompiledOptimizationProblem.VERSION, input, catalog, dependencies, paths,
                CompiledOptimizationProblem.reference(input, catalog, dependencies, paths), dataHash, run.runSpecificationSha256());
    }

    private LoadedSandboxBaseline authenticate(LoadedSandboxBaseline supplied) {
        Objects.requireNonNull(supplied, "baseline");
        try {
            var loaded = new SandboxBaselineLoader(json).load(new ByteArrayResource(json.writeValueAsBytes(supplied.sourceJson())));
            if (!json.valueToTree(loaded.baseline()).equals(json.valueToTree(supplied.baseline())))
                throw new IllegalArgumentException("Baseline object differs from authenticated source");
            return loaded;
        } catch (java.io.IOException failure) { throw new IllegalArgumentException("Invalid frozen baseline", failure); }
    }

    private JsonNode businessFacts(JsonNode node) {
        if (node.isObject()) {
            ((ObjectNode) node).remove(List.of("name", "stageName", "chainName", "description", "driverName", "driverPhone", "licensePlate"));
            node.elements().forEachRemaining(this::businessFacts);
        } else if (node.isArray()) node.elements().forEachRemaining(this::businessFacts);
        return node;
    }

    private ProductionTaskCatalog catalog(OptimizationInputManifest input, ProductionFactsAdapter facts, String rootSeed) {
        var protocol = new SandboxRandomProtocol(json);
        var batches = new ArrayList<Batch>(); var flows = new ArrayList<Flow>(); var tasks = new ArrayList<Task>();
        for (int loop = 0; loop < input.totalLoops(); loop = Math.addExact(loop, 6)) {
            for (var chain : facts.chains) {
                if (chain.getStatus() != ProcessingChain.ChainStatus.ACTIVE) continue;
                compileBatch(loop, chain, facts, rootSeed, protocol, batches, flows, tasks);
            }
        }
        return new ProductionTaskCatalog(ProductionTaskCatalog.VERSION, input.fingerprint(), input.totalLoops(), batches, flows, tasks);
    }

    private void compileBatch(int loop, ProcessingChain chain, ProductionFactsAdapter facts, String rootSeed,
            SandboxRandomProtocol protocol, List<Batch> batches, List<Flow> flows, List<Task> tasks) {
        var stages = chain.getStages();
        var edges = ProcessingChainGraphValidator.normalizedEdges(stages, chain.getEdges());
        var order = ProcessingChainGraphValidator.topologicalOrder(stages, edges);
        var sink = stages.stream().filter(s -> edges.stream().noneMatch(e -> e.fromStage() == s)).findFirst().orElseThrow();
        var sinkInputs = ProcessingChainGraphValidator.inputs(sink);
        if (sinkInputs.size() != 1) throw new IllegalArgumentException("Terminal stage requires one transported input");
        Goods finalGoods = requireGoods(facts, sinkInputs.get(0).sku());
        int quantity = ProductionRules.finalQuantity(protocol.random(rootSeed, SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY,
                Map.of("loopIndex", loop, "processingChainId", chain.getId()))::nextInt);
        double demand = ProductionRules.round(quantity * finalGoods.getWeightPerUnit());
        var calculation = ProductionRules.calculatePlan(stages, edges, order, sink, demand);
        var selected = ProductionRules.selectAutomatic(stages, edges, sink, loop, chain.getId(),
                stage -> facts.pois.values().stream().filter(p -> p.getPoiType() == stage.getProcessingPOI().getPoiType())
                        .sorted(Comparator.comparing(POI::getId)).toList(), ProductionRules::distanceKm,
                (domain, key) -> protocol.random(rootSeed, domain, key));
        var batchKey = new BatchKey(loop, chain.getId());
        var actualFlowWeights = new IdentityHashMap<ProcessingChainGraphValidator.EdgeSpec, Double>();
        var stageFacts = new ArrayList<Stage>();
        for (var stage : order) {
            var incoming = edges.stream().filter(e -> e.toStage() == stage).toList();
            Role role = incoming.isEmpty() ? Role.SOURCE : stage == sink ? Role.SINK : Role.PROCESSING;
            double plannedInput = role == Role.SOURCE ? 0 : calculation.inputWeights().get(stage).values().stream().mapToDouble(Double::doubleValue).sum();
            double actualInput = role == Role.SOURCE ? 0 : ProductionRules.round(incoming.stream()
                    .mapToDouble(e -> actualFlowWeights.get(e)).sum());
            double actualOutput = role == Role.SOURCE ? ProductionRules.round(calculation.outputs().get(stage))
                    : role == Role.SINK ? actualInput : ProductionRules.completedOutput(actualInput, stage.getOutputWeightRatio());
            if (!Double.isFinite(actualOutput) || actualOutput <= 0) throw new IllegalArgumentException("Invalid derived production output");
            stageFacts.add(new Stage(new StageKey(batchKey, stage.getId()), role, selected.get(stage).getId(),
                    role == Role.PROCESSING ? Math.multiplyExact(stage.getProcessingTimeMinutes().longValue(), 60) : 0,
                    decimal(ProductionRules.round(plannedInput)), decimal(ProductionRules.round(calculation.outputs().get(stage))),
                    decimal(actualInput), decimal(actualOutput)));
            for (var edge : edges) if (edge.fromStage() == stage) {
                // Source release uses planned flow weight; subsequent release uses completed output.
                double planned = calculation.inputWeights().get(edge.toStage()).get(edge.input().inputKey());
                double transported = role == Role.SOURCE ? planned : actualOutput;
                actualFlowWeights.put(edge, transported);
                var parts = ProductionRules.split(requireGoods(facts, edge.input().sku()), transported, facts.vehicles);
                var key = new FlowKey(batchKey, stage.getId(), edge.toStage().getId(), edge.input().inputKey());
                flows.add(new Flow(key, edge.input().sku(), selected.get(stage).getId(), selected.get(edge.toStage()).getId(),
                        decimal(planned), decimal(transported), decimal(parts.stream().mapToDouble(p -> p.volume()).sum())));
                for (int i = 0; i < parts.size(); i++) {
                    var part = parts.get(i);
                    tasks.add(new Task(new GlobalPlan.TaskKey(loop, chain.getId(), stage.getId(), edge.toStage().getId(),
                            edge.input().inputKey(), i), key, part.quantity(), decimal(part.weight()), decimal(part.volume())));
                }
            }
        }
        batches.add(new Batch(batchKey, quantity, decimal(demand), stageFacts));
    }
    private static Goods requireGoods(ProductionFactsAdapter facts, String sku) {
        Goods item = facts.goods.get(sku);
        if (item == null) throw new IllegalArgumentException("Missing transported goods: " + sku);
        return item;
    }
    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value); }
}
