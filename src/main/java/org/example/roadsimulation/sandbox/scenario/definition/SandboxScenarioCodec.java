package org.example.roadsimulation.sandbox.scenario.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.sandbox.baseline.EffectiveBaseDataCodec;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;

import java.util.Comparator;
import java.util.List;

/** Canonical JSON fingerprints for published scenario facts. */
public final class SandboxScenarioCodec {
    public static final String CANONICALIZATION = "lexicographic-json-v1";

    private final ObjectMapper objectMapper;
    private final LexicographicJsonSha256 jsonHash;
    private final EffectiveBaseDataCodec baseDataCodec;

    public SandboxScenarioCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonHash = new LexicographicJsonSha256(objectMapper);
        this.baseDataCodec = new EffectiveBaseDataCodec(objectMapper);
    }

    public String definitionHash(
            SandboxScenarioDefinitionV1 definition,
            SandboxScenarioRevisionV1.ResolvedSelection resolved
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("baseline", objectMapper.valueToTree(definition.baseline()));
        payload.set("selection", objectMapper.valueToTree(definition.selection()));
        payload.set("overrides", objectMapper.valueToTree(definition.overrides()));
        payload.set("resolvedSelection", objectMapper.valueToTree(resolved));
        return jsonHash.hash(payload);
    }

    public String baseDataProjectionHash(
            String eligibilityPolicyVersion,
            SandboxBaselinePackageV1.Data data
    ) {
        return baseDataCodec.hash(
                org.example.roadsimulation.sandbox.baseline.EffectiveBaseData.ALL_ELIGIBLE_V1,
                eligibilityPolicyVersion,
                data);
    }

    public String effectiveDataHash(
            SandboxBaselinePackageV1.Data data,
            List<EffectiveScenarioData.VehicleInitialization> vehicleInitializations
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("data", objectMapper.valueToTree(baseDataCodec.normalize(data)));
        payload.set("vehicleInitializations", objectMapper.valueToTree(vehicleInitializations.stream()
                .sorted(Comparator.comparingLong(EffectiveScenarioData.VehicleInitialization::vehicleId))
                .toList()));
        return jsonHash.hash(payload);
    }

    public SandboxBaselinePackageV1.Data normalizeData(SandboxBaselinePackageV1.Data data) {
        return baseDataCodec.normalize(data);
    }
}
