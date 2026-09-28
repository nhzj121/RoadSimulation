package org.example.roadsimulation.sandbox.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioSnapshotDesignBaselineTest {

    private static final Path BASELINE = Path.of(
            "doc", "architecture", "sandbox", "formal-design-baseline-v0.json");

    @Test
    void currentDatabaseDesignBaselineIsCompleteAsAnArtifactAndExplicitlyNotExecutionReady()
            throws Exception {
        JsonNode artifact = new ObjectMapper().readTree(Files.readString(BASELINE));
        JsonNode counts = artifact.path("source").path("tableRowCounts");
        JsonNode facts = artifact.path("scenarioSnapshot");

        assertEquals("FORMAL_DESIGN_BASELINE_INCOMPLETE", artifact.path("baselineStatus").asText());
        assertFalse(artifact.path("executionReady").asBoolean());
        assertEquals(counts.path("poi").asInt(), facts.path("pois").size());
        assertEquals(counts.path("goods").asInt(), facts.path("goods").size());
        assertEquals(counts.path("vehicle").asInt(), facts.path("vehicles").size());
        assertEquals(counts.path("processing_chain").asInt(), facts.path("processingChains").size());
        assertEquals(counts.path("processing_stage").asInt(),
                facts.path("processingChains").findValues("stages").stream()
                        .mapToInt(JsonNode::size).sum());
        assertEquals(counts.path("processing_stage_input").asInt(),
                facts.path("processingChains").findValues("inputs").stream()
                        .mapToInt(JsonNode::size).sum());
        assertEquals(counts.path("processing_stage_edge").asInt(),
                facts.path("processingChains").findValues("edges").stream()
                        .mapToInt(JsonNode::size).sum());
        assertEquals(counts.path("poi").asInt(), artifact.path("restoreLabels").path("pois").size());
        assertTrue(facts.path("paths").isEmpty());
        assertEquals(15, facts.path("processingChains").findValues("legacyInputWeightRatio")
                .stream().filter(JsonNode::isNull).count());
        assertEquals(15, facts.path("processingChains").findValues("outputWeightRatio")
                .stream().filter(JsonNode::isNull).count());

        ScenarioSnapshotCodec codec = new ScenarioSnapshotCodec();
        ScenarioSnapshot snapshot = codec.fromJson(facts.toString());
        assertEquals(artifact.path("snapshotFingerprintSha256").asText(), codec.fingerprint(snapshot));
    }
}
