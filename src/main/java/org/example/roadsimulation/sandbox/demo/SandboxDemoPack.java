package org.example.roadsimulation.sandbox.demo;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.sandbox.baseline.*;
import org.example.roadsimulation.sandbox.run.*;
import org.example.roadsimulation.sandbox.scenario.definition.*;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Developer-maintained real-input demonstrations. Offline only: no database or execution. */
public final class SandboxDemoPack {
    public record Entry(String id, String title, String scenarioKey, CompiledSandboxRunSpecificationV2 run) {}
    public record Pack(Map<String,CompiledSandboxScenario> scenarios, List<Entry> entries, ObjectNode catalog) {}
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public Pack compile() throws Exception {
        var loader = new SandboxBaselineLoader(json);
        var baseline = loader.load(new ClassPathResource("sandbox/baseline/baseline-v1.json"));
        var scenarioCompiler = new SandboxScenarioCompiler(json);
        var definition = scenarioCompiler.readDefinition(new ClassPathResource("sandbox/scenarios/default-all-eligible-v1.json"));
        Map<String,CompiledSandboxScenario> scenarios = new LinkedHashMap<>();
        for (String group : List.of("full", "small", "matched")) {
            ObjectNode node = json.valueToTree(definition);
            node.put("scenarioKey", "demo-v1-" + group);
            node.put("displayName", switch(group) { case "small" -> "演示：小载重车队"; case "matched" -> "演示：等额定载重大车车队"; default -> "演示：全部合格车辆"; });
            node.put("description", "固定正式基线；演示包v1；载货容积保留原值，不作为匹配条件。");
            var selection = (ObjectNode)node.path("selection").path("vehicles");
            if (group.equals("small")) selection.put("maximumLoadCapacityTonnes", new BigDecimal("2"));
            if (group.equals("matched")) {
                selection.put("mode", "EXPLICIT_IDS");
                var ids = selection.putArray("includeIds");
                // Stable IDs, not a runtime search for a result-favourable fleet.
                for (long id : new long[]{17,29,41,46,52,57,63,68}) ids.add(id);
            }
            var compiled = scenarioCompiler.compile(baseline, json.treeToValue(node, SandboxScenarioDefinitionV1.class));
            scenarios.put(compiled.normalizedDefinition().scenarioKey(), compiled);
        }
        var runCompiler = new SandboxRunCompilerV2(json);
        var template = runCompiler.read(new ClassPathResource("sandbox/runs/default-production-original-v2.json"));
        var titles = List.of("A 参考组", "B HEURISTIC策略", "C 持续降雨", "D 小载重资源受限", "E 等额定载重大车", "F 自动事件");
        var suffixes = List.of("a-reference", "b-strategy", "c-rain", "d-small", "e-matched", "f-events");
        List<Entry> entries = new ArrayList<>();
        for (int i=0;i<suffixes.size();i++) {
            String id = suffixes.get(i), sceneKey = "demo-v1-" + (i==3?"small":i==4?"matched":"full");
            var scene = scenarios.get(sceneKey);
            ObjectNode node = json.valueToTree(template);
            node.put("runSpecKey", "demo-v1-"+id).put("displayName", titles.get(i));
            node.put("description", "开发演示v1：48轮，每轮30分钟，种子20260927；比较配对见pack.json。");
            var binding = (ObjectNode)node.path("scenario");
            binding.put("scenarioKey",sceneKey).put("revision",1)
                    .put("scenarioDefinitionSha256",scene.scenarioDefinitionSha256())
                    .put("effectiveScenarioDataSha256",scene.effectiveData().effectiveScenarioDataSha256());
            if(i==1) ((ObjectNode)node.path("dispatch")).put("strategy","HEURISTIC")
                    .put("algorithmProfileId", SandboxAlgorithmProfiles.HEURISTIC_V1);
            if(i==2) {
                var weather = (ObjectNode)node.path("weather");
                weather.put("sourceMode","EXPLICIT_TIMELINE");
                for(String field:List.of("generatorVersion","intervalMinutes","weights","speedFactors")) weather.putNull(field);
                weather.putArray("timeSlices").addObject().put("startMinute",0).put("endMinute",1440)
                        .put("weatherType","RAIN").put("speedFactor",0.8);
            }
            if(i==5) ((ObjectNode)node.path("events")).put("autoEnabled",true);
            var revision = new SandboxScenarioRevisionV1(SandboxScenarioRevisionV1.ARTIFACT_VERSION,sceneKey,1,
                    scene.normalizedDefinition(),scene.resolvedSelection(),new SandboxScenarioRevisionV1.Fingerprints(
                    SandboxScenarioCodec.CANONICALIZATION,scene.scenarioDefinitionSha256(),scene.effectiveData().baseDataProjectionSha256(),
                    scene.effectiveData().effectiveScenarioDataSha256()),Instant.parse("2026-01-01T00:00:00Z"));
            entries.add(new Entry(id,titles.get(i),sceneKey,runCompiler.compile(json.treeToValue(node,SandboxRunSpecificationV2.class),revision,scene)));
        }
        var catalog = json.createObjectNode().put("artifactVersion","sandbox-demo-pack/v1").put("packId","developer-demo-v1")
                .put("baselineId",baseline.baseline().baselineId()).put("rootSeed","20260927").put("totalLoops",48)
                .put("tickDurationSeconds",1800).put("startLocalDateTime","2026-01-01T00:00:00")
                .put("matchingScope","TOTAL_RATED_PAYLOAD_ONLY")
                .put("interpretation","D/E总额定载重接近；容积、车辆数量、司机及车型差异保留，不能归因为单一车辆大小效应。");
        var sceneStats = catalog.putObject("fleets");
        scenarios.forEach((key,scene)->{
            var vehicles=scene.effectiveData().data().vehicles();
            var stats=sceneStats.putObject(key).put("vehicleCount",vehicles.size())
                    .put("ratedPayloadTonnes",vehicles.stream().map(SandboxBaselinePackageV1.Vehicle::maxLoadCapacityTonnes).reduce(BigDecimal.ZERO,BigDecimal::add))
                    .put("cargoVolumeCubicMeters",vehicles.stream().map(SandboxBaselinePackageV1.Vehicle::cargoVolumeCubicMeters).reduce(BigDecimal.ZERO,BigDecimal::add));
            stats.set("vehicleIds",json.valueToTree(vehicles.stream().map(SandboxBaselinePackageV1.Vehicle::id).toList()));
        });
        var pairs=catalog.putArray("pairs");
        for(var pair:List.of(new String[]{"a-reference","b-strategy","调度策略"},new String[]{"a-reference","c-rain","持续降雨"},
                new String[]{"a-reference","d-small","资源受限"},new String[]{"d-small","e-matched","总额定载重接近的车队结构"},
                new String[]{"a-reference","f-events","自动事件"}))
            pairs.addObject().put("left",pair[0]).put("right",pair[1]).put("title",pair[2]);
        return new Pack(Collections.unmodifiableMap(scenarios),List.copyOf(entries),catalog);
    }

    public void export(Path directory) throws Exception {
        var pack=compile();
        // CREATE_DIRECTORY fails for an existing destination; never overwrite a previously generated pack.
        Files.createDirectory(directory);Files.createDirectory(directory.resolve("scenarios"));Files.createDirectory(directory.resolve("runs"));
        var catalog=pack.catalog().deepCopy();var files=catalog.putObject("files");
        for(var scene:pack.scenarios().values()) write(directory,"scenarios/"+scene.normalizedDefinition().scenarioKey()+".json",scene.normalizedDefinition(),files);
        var entries=catalog.putArray("entries");
        for(var entry:pack.entries()) {
            String file="runs/"+entry.run().normalizedSpecification().runSpecKey()+".json";
            write(directory,file,entry.run().normalizedSpecification(),files);
            entries.addObject().put("id",entry.id()).put("title",entry.title()).put("scenarioKey",entry.scenarioKey())
                    .put("scenarioFile","scenarios/"+entry.scenarioKey()+".json").put("runFile",file)
                    .put("runSpecKey",entry.run().normalizedSpecification().runSpecKey())
                    .put("runSpecificationSha256",entry.run().runSpecificationSha256());
        }
        Files.writeString(directory.resolve("pack.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(catalog)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    private void write(Path root,String relative,Object value,ObjectNode hashes) throws Exception {
        byte[] bytes=(json.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n").getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve(relative),bytes,StandardOpenOption.CREATE_NEW);
        hashes.put(relative,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("One new output directory required");
        new SandboxDemoPack().export(Path.of(args[0]));
        System.out.println("DEMO_PACK_EXPORTED "+Path.of(args[0]).toAbsolutePath());
    }
}
