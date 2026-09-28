package org.example.roadsimulation.sandbox.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxBaselinePackageV1Test {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SandboxBaselineLoader loader = new SandboxBaselineLoader(objectMapper);
    private final ClassPathResource baseline = new ClassPathResource(
            "sandbox/baseline/baseline-v1.json");

    @Test
    void authenticatesFormalBaselineAndBuildsAllEligibleData() {
        LoadedSandboxBaseline loaded = loader.load(baseline);
        EffectiveBaseData effective = loader.selectAllEligible(loaded);

        assertEquals("roadsimulation-baseline-20260928-01", loaded.baseline().baselineId());
        assertEquals(2603, loaded.baseline().data().pois().size());
        assertEquals(11, loaded.baseline().data().goods().size());
        assertEquals(87, loaded.baseline().data().vehicles().size());
        assertEquals(2602, effective.data().pois().size());
        assertEquals(10, effective.data().goods().size());
        assertEquals(85, effective.data().vehicles().size());
        assertEquals(4, effective.data().processingChains().size());
        assertEquals(15, effective.data().processingChains().stream()
                .mapToInt(chain -> chain.stages().size()).sum());
        assertEquals(15, effective.data().processingChains().stream()
                .flatMap(chain -> chain.stages().stream())
                .mapToInt(stage -> stage.inputs().size()).sum());
        assertEquals(11, effective.data().processingChains().stream()
                .mapToInt(chain -> chain.edges().size()).sum());
        assertTrue(effective.data().initialInventories().isEmpty());
        assertFalse(effective.data().goods().stream().anyMatch(value -> value.id() == 3));
        assertFalse(effective.data().vehicles().stream().anyMatch(value -> value.id() == 5 || value.id() == 10));
        assertFalse(effective.data().pois().stream().anyMatch(value -> value.id() == 3466));
        assertEquals("8f208b56e1350f26af46bc666c36c7bc1c65209d6b0c1b155bf9036e1d922922",
                effective.effectiveBaseDataSha256());
        assertEquals(255, effective.data().drivers().size());
        assertEquals(255, effective.data().driverVehicleBindings().size());
    }

    @Test
    void rejectsTamperedRestorationPayload() throws Exception {
        String json = baseline.getContentAsString(StandardCharsets.UTF_8);
        String tampered = json.replaceFirst("104\\.176486", "104.176487");

        SandboxBaselineException exception = assertThrows(
                SandboxBaselineException.class,
                () -> loader.load(new ByteArrayResource(tampered.getBytes(StandardCharsets.UTF_8)))
        );

        assertTrue(exception.getMessage().contains("restorationPayloadSha256 mismatch"));
    }

    @Test
    void rejectsDuplicateIdsAndBrokenDependencyClosure() {
        EffectiveBaseData effective = loader.selectAllEligible(loader.load(baseline));
        SandboxBaselineValidator validator = new SandboxBaselineValidator();

        var duplicatePois = new ArrayList<>(effective.data().pois());
        duplicatePois.add(effective.data().pois().get(0));
        var duplicateData = new SandboxBaselinePackageV1.Data(
                duplicatePois, effective.data().goods(), effective.data().vehicles(),
                effective.data().processingChains(), effective.data().initialInventories());
        assertThrows(SandboxBaselineException.class, () -> validator.validateEffectiveData(duplicateData));

        var missingTemplatePoi = effective.data().pois().stream()
                .filter(value -> value.id() != 4762L)
                .toList();
        var brokenData = new SandboxBaselinePackageV1.Data(
                missingTemplatePoi, effective.data().goods(), effective.data().vehicles(),
                effective.data().processingChains(), effective.data().initialInventories());
        assertThrows(SandboxBaselineException.class, () -> validator.validateEffectiveData(brokenData));
    }
}
