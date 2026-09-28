package org.example.roadsimulation.sandbox.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.example.roadsimulation.sandbox.scenario.ScenarioSnapshot.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioSnapshotCodecTest {

    private final ScenarioSnapshotCodec codec = new ScenarioSnapshotCodec();

    @Test
    void v0FixtureHasPinnedProtocolFingerprint() {
        assertEquals(
                "13fb292c3e0917f4f9b7208f86efa2467893d383e76c57b6fe303ee5958f3134",
                codec.fingerprint(fixture(false, false))
        );
    }

    @Test
    void canonicalJsonAndFingerprintIgnoreCollectionOrderAndDecimalScale() {
        ScenarioSnapshot ordered = fixture(false, false);
        ScenarioSnapshot reversedAndRescaled = fixture(true, true);

        String expectedJson = codec.toCanonicalJson(ordered);
        String expectedFingerprint = codec.fingerprint(ordered);

        assertEquals(expectedJson, codec.toCanonicalJson(reversedAndRescaled));
        assertEquals(expectedFingerprint, codec.fingerprint(reversedAndRescaled));

        for (int attempt = 0; attempt < 1_000; attempt++) {
            assertEquals(expectedJson, codec.toCanonicalJson(ordered));
            assertEquals(expectedFingerprint, codec.fingerprint(ordered));
        }
    }

    @Test
    void roundTripPreservesTheCanonicalContractAndPathSegments() {
        ScenarioSnapshot canonical = codec.canonicalize(fixture(true, true));
        String json = codec.toCanonicalJson(canonical);
        ScenarioSnapshot restored = codec.fromJson(json);

        assertEquals(canonical, restored);
        assertEquals(List.of(0, 1), restored.paths().get(0).segments().stream()
                .map(PathSegmentFact::sequence)
                .toList());
        assertFalse(json.contains("createdAt"));
        assertFalse(json.contains("updatedAt"));
        assertFalse(json.contains("refNo"));
    }

    @Test
    void detectsAValidButTamperedBusinessValue() {
        ScenarioSnapshot original = fixture(false, false);
        String fingerprint = codec.fingerprint(original);
        ScenarioSnapshot tampered = copyWithInventoryQuantity(original, 101);

        ScenarioSnapshotIntegrityException exception = assertThrows(
                ScenarioSnapshotIntegrityException.class,
                () -> codec.verifyFingerprint(tampered, fingerprint)
        );
        assertTrue(exception.getMessage().contains("fingerprint mismatch"));
        codec.verifyFingerprint(original, fingerprint.toUpperCase());
    }

    @Test
    void rejectsMissingBusinessReferencesBeforeHashing() {
        ScenarioSnapshot original = fixture(false, false);
        VehicleFact invalidVehicle = new VehicleFact(
                42, "V-042", decimal("20"), decimal("45"), "Brand", "Model", "TRUCK",
                false, null, null, decimal("6"), decimal("2.5"), decimal("3"), "G07",
                "IDLE", 999L, null, null, decimal("0"), decimal("0"), 0
        );
        ScenarioSnapshot invalid = new ScenarioSnapshot(
                original.schemaVersion(), original.pois(), original.goods(), List.of(invalidVehicle),
                original.inventories(), original.initialDemands(), original.processingChains(), original.paths()
        );

        ScenarioSnapshotValidationException exception = assertThrows(
                ScenarioSnapshotValidationException.class,
                () -> codec.fingerprint(invalid)
        );
        assertTrue(exception.getMessage().contains("references missing id: 999"));
    }

    @Test
    void rejectsUnknownFieldsAndUnsupportedContractVersions() {
        String json = codec.toCanonicalJson(fixture(false, false));
        String withUnknownField = json.substring(0, json.length() - 1) + ",\"unexpected\":true}";

        assertThrows(IllegalArgumentException.class, () -> codec.fromJson(withUnknownField));

        ScenarioSnapshot original = fixture(false, false);
        ScenarioSnapshot unsupported = new ScenarioSnapshot(
                "scenario-snapshot/v1", original.pois(), original.goods(), original.vehicles(),
                original.inventories(), original.initialDemands(), original.processingChains(), original.paths()
        );
        ScenarioSnapshotValidationException exception = assertThrows(
                ScenarioSnapshotValidationException.class,
                () -> codec.toCanonicalJson(unsupported)
        );
        assertTrue(exception.getMessage().contains("unsupported schemaVersion"));
    }

    @Test
    void rejectsAmbiguousPathSelectionsAndBrokenProcessingEdges() {
        ScenarioSnapshot original = fixture(false, false);
        PathFact duplicateSelection = new PathFact(
                "alternate-key", 10, 30, "0", decimal("2100"), decimal("240"),
                decimal("5"), decimal("1000"), "116.1,39.7;116.3,39.9",
                original.paths().get(0).segments()
        );
        ScenarioSnapshot ambiguous = new ScenarioSnapshot(
                original.schemaVersion(), original.pois(), original.goods(), original.vehicles(),
                original.inventories(), original.initialDemands(), original.processingChains(),
                List.of(original.paths().get(0), duplicateSelection)
        );
        assertThrows(ScenarioSnapshotValidationException.class, () -> codec.fingerprint(ambiguous));

        ProcessingChainFact chain = original.processingChains().get(0);
        ProcessingChainFact brokenChain = new ProcessingChainFact(
                chain.id(), chain.chainCode(), chain.status(), chain.stages(),
                List.of(new ProcessingEdgeFact(3000, 1000, 1005, 9999))
        );
        ScenarioSnapshot broken = new ScenarioSnapshot(
                original.schemaVersion(), original.pois(), original.goods(), original.vehicles(),
                original.inventories(), original.initialDemands(), List.of(brokenChain), original.paths()
        );
        ScenarioSnapshotValidationException exception = assertThrows(
                ScenarioSnapshotValidationException.class,
                () -> codec.fingerprint(broken)
        );
        assertTrue(exception.getMessage().contains("edge.toStageInputId references missing id: 9999"));
    }

    private ScenarioSnapshot fixture(boolean reverse, boolean rescale) {
        List<PoiFact> pois = mutable(
                new PoiFact(10, number("116.100000", rescale), number("39.700000", rescale), "WAREHOUSE"),
                new PoiFact(30, number("116.300000", rescale), number("39.900000", rescale), "SAWMILL")
        );
        List<GoodsFact> goods = mutable(
                new GoodsFact(7, "G07", "RAW", number("1.500", rescale), number("2.000", rescale),
                        false, null, null, "TRUCK"),
                new GoodsFact(11, "G11", "PROCESSED", number("2.500", rescale), number("3.000", rescale),
                        false, null, null, "TRUCK")
        );
        List<VehicleFact> vehicles = mutable(new VehicleFact(
                42, "V-042", number("20.000", rescale), number("45.000", rescale),
                "Brand", "Model", "TRUCK", false, null, null,
                number("6.000", rescale), number("2.500", rescale), number("3.000", rescale),
                "G07", "IDLE", 10L, null, null,
                number("0.000", rescale), number("0.000", rescale), 0
        ));
        List<InventoryFact> inventories = mutable(
                new InventoryFact(10, 7, 100),
                new InventoryFact(30, 11, 0)
        );
        List<InitialDemandFact> demands = mutable(new InitialDemandFact(
                "D-001", 10, 30, 0L, 3_600L,
                mutable(new DemandItemFact(
                        "I-001", 7, 10, number("15.000", rescale), number("20.000", rescale)
                ))
        ));

        ProcessingStageFact firstStage = new ProcessingStageFact(
                1000, 1, "CUT", 10, 7L, "G07", number("1.000", rescale),
                11L, "G11", number("0.950", rescale), 45,
                number("1.000", rescale), number("100.000", rescale),
                mutable(new ProcessingInputFact(2000, "raw", 7L, "G07", number("1.000", rescale)))
        );
        ProcessingStageFact secondStage = new ProcessingStageFact(
                1005, 2, "FINISH", 30, 11L, "G11", number("1.000", rescale),
                11L, "G11", number("0.900", rescale), 60,
                number("1.000", rescale), number("80.000", rescale),
                mutable(new ProcessingInputFact(2005, "semi", 11L, "G11", number("1.000", rescale)))
        );
        List<ProcessingStageFact> stages = mutable(firstStage, secondStage);
        if (reverse) {
            reverse(stages);
        }
        ProcessingChainFact chain = new ProcessingChainFact(
                100, "C100", "ACTIVE", stages,
                mutable(new ProcessingEdgeFact(3000, 1000, 1005, 2005))
        );
        List<ProcessingChainFact> chains = mutable(chain);

        List<PathSegmentFact> segments = mutable(
                new PathSegmentFact(0, "Road A", "Start", "直行", "东",
                        number("1000.000", rescale), number("120.000", rescale), "116.1,39.7;116.2,39.8"),
                new PathSegmentFact(1, "Road B", "Turn", "右转", "北",
                        number("1000.000", rescale), number("120.000", rescale), "116.2,39.8;116.3,39.9")
        );
        if (reverse) {
            reverse(segments);
        }
        List<PathFact> paths = mutable(new PathFact(
                "P-10-30-S0", 10, 30, "0", number("2000.000", rescale),
                number("240.000", rescale), number("5.000", rescale),
                number("1000.000", rescale), "116.1,39.7;116.2,39.8;116.3,39.9", segments
        ));

        if (reverse) {
            reverse(pois);
            reverse(goods);
            reverse(inventories);
        }
        return new ScenarioSnapshot(
                SCHEMA_VERSION, pois, goods, vehicles, inventories, demands, chains, paths
        );
    }

    private ScenarioSnapshot copyWithInventoryQuantity(ScenarioSnapshot source, int quantity) {
        List<InventoryFact> inventories = source.inventories().stream()
                .map(value -> value.poiId() == 10 && value.goodsId() == 7
                        ? new InventoryFact(value.poiId(), value.goodsId(), quantity)
                        : value)
                .toList();
        return new ScenarioSnapshot(
                source.schemaVersion(), source.pois(), source.goods(), source.vehicles(), inventories,
                source.initialDemands(), source.processingChains(), source.paths()
        );
    }

    @SafeVarargs
    private static <T> List<T> mutable(T... values) {
        return new ArrayList<>(List.of(values));
    }

    private static void reverse(List<?> values) {
        Collections.reverse(values);
    }

    private static BigDecimal number(String value, boolean rescale) {
        BigDecimal decimal = decimal(value);
        return rescale ? decimal.setScale(decimal.scale() + 2) : decimal;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
