package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.service.GaodeMapService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Freezes display geometry outside the execution database and worker lifecycle. */
@RestController
@RequestMapping("/api/sandbox/executions/{id}/road-replay/routes")
@Profile("!sandbox-runtime & !sandbox-controlled-runtime")
@ConditionalOnProperty(name = "sandbox.management.enabled", havingValue = "true")
public class SandboxRoadReplayRoutes {
    private static final String STRATEGY = "0";
    private static final String VERSION = "sandbox-gaode-road/v1";
    private final SandboxManagementService management;
    private final GaodeMapService maps;
    private final ObjectMapper json;
    private final Path root;

    public SandboxRoadReplayRoutes(SandboxManagementService management, GaodeMapService maps, ObjectMapper json,
                                   Environment environment) {
        this.management = management;
        this.maps = maps;
        this.json = json;
        this.root = Path.of(environment.getProperty("sandbox.management.road-replay-directory", "runtime-logs/sandbox-road-replay"));
    }

    public record Prepare(int loopIndex, long legId) {}
    public record Point(double longitude, double latitude) {}
    public record Road(String artifactVersion, String routeKey, List<Point> points) {}

    @PostMapping
    public Road prepare(@PathVariable String id, @RequestBody Prepare request) {
        JsonNode tick = management.replayTick(id, request.loopIndex());
        for (JsonNode leg : tick.path("facts").path("legs")) {
            if (leg.path("id").asLong() != request.legId()) continue;
            if (!leg.path("startedSimTime").isTextual() || leg.path("distanceMeters").asDouble() <= 0
                    || !leg.path("from").isObject() || !leg.path("to").isObject()) break;
            String from = coordinates(leg.path("from"));
            String to = coordinates(leg.path("to"));
            String key = digest(String.join("|", VERSION, id, tick.path("manifestSha256").asText(), STRATEGY, from, to));
            return frozenRoute(key, from, to);
        }
        throw new SandboxWorkspaceException("REPLAY_ROAD_NOT_FOUND", "No road-plannable recorded leg");
    }

    private synchronized Road frozenRoute(String key, String from, String to) {
        try {
            Files.createDirectories(root);
            Path file = root.resolve(key + ".json");
            try (var channel = FileChannel.open(root.resolve("freeze.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                if (Files.exists(file)) {
                    Road frozen = json.readValue(file.toFile(), Road.class);
                    if (!VERSION.equals(frozen.artifactVersion()) || !key.equals(frozen.routeKey())
                            || frozen.points() == null || frozen.points().size() < 2) {
                        throw new SandboxWorkspaceException("REPLAY_ROAD_STORAGE_FAILED", "Invalid frozen road geometry");
                    }
                    return frozen;
                }
                var request = new GaodeRouteRequest(from, to);
                request.setStrategy(STRATEGY);
                var response = maps.planDrivingRoute(request);
                if (response == null || !response.isSuccess() || response.getData() == null
                        || response.getData().getPaths() == null || response.getData().getPaths().isEmpty()) {
                    throw new SandboxWorkspaceException("REPLAY_ROAD_UNAVAILABLE", "Highway geometry could not be planned");
                }
                String line = response.getData().getPaths().get(0).getPolyline();
                Road road = new Road(VERSION, key, parsePoints(line));
                Path temporary = Files.createTempFile(root, key, ".tmp");
                try {
                    json.writeValue(temporary.toFile(), road);
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(temporary);
                }
                return road;
            }
        } catch (IOException failure) {
            throw new SandboxWorkspaceException("REPLAY_ROAD_STORAGE_FAILED", "Unable to read or freeze road geometry", failure);
        }
    }

    private static List<Point> parsePoints(String line) {
        var points = new ArrayList<Point>();
        try {
            for (String pair : Objects.toString(line, "").split(";")) {
                var parts = pair.split(",");
                if (parts.length != 2) throw new IllegalArgumentException();
                double longitude = Double.parseDouble(parts[0]);
                double latitude = Double.parseDouble(parts[1]);
                if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                        || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) throw new IllegalArgumentException();
                var point = new Point(longitude, latitude);
                if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) points.add(point);
            }
        } catch (IllegalArgumentException failure) {
            throw new SandboxWorkspaceException("REPLAY_ROAD_UNAVAILABLE", "Invalid Gaode road geometry", failure);
        }
        if (points.size() < 2) throw new SandboxWorkspaceException("REPLAY_ROAD_UNAVAILABLE", "No usable Gaode road geometry");
        return List.copyOf(points);
    }

    private static String coordinates(JsonNode point) {
        return point.path("longitude").decimalValue().stripTrailingZeros().toPlainString()
                + "," + point.path("latitude").decimalValue().stripTrailingZeros().toPlainString();
    }

    private static String digest(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
