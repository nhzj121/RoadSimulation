package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/** Calls the map service between two short reads of the current reservation. */
@Service
@Profile("!sandbox-runtime")
public class ManualReplacementRoutePlanner {
    private final ManualReplacementService replacements;
    private final RoutePlanningService routes;

    public ManualReplacementRoutePlanner(ManualReplacementService replacements, RoutePlanningService routes) {
        this.replacements = replacements;
        this.routes = routes;
    }

    public record Route(long attemptId, List<List<Double>> path) {}

    public Route plan(long id, ManualReplacementService.Operation operation) {
        var approach = replacements.approach(id, operation);
        var start = List.of(approach.startLongitude(), approach.startLatitude());
        var end = List.of(approach.faultLongitude(), approach.faultLatitude());
        if (start.equals(end)) return new Route(operation.attemptId(), List.of(start));

        var request = new GaodeRouteRequest(coordinate(start), coordinate(end));
        request.setStrategy("0");
        GaodeRouteResponse response = routes.planRouteWithGaode(request);
        var path = readPath(response);
        if (!approach.equals(replacements.approach(id, operation))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "接替车辆已变化，请刷新接替状态");
        }
        var connected = new ArrayList<List<Double>>(path.size() + 2);
        connected.add(start);
        path.forEach(point -> append(connected, point));
        append(connected, end);
        return new Route(operation.attemptId(), List.copyOf(connected));
    }

    private static List<List<Double>> readPath(GaodeRouteResponse response) {
        if (response == null || !response.isSuccess() || response.getData() == null
                || response.getData().getPaths() == null || response.getData().getPaths().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "高德赶赴路线规划失败");
        }
        var route = response.getData().getPaths().get(0);
        var points = new ArrayList<List<Double>>();
        if (route.getSteps() != null) {
            route.getSteps().forEach(step -> appendPolyline(points, step.getPolyline()));
        }
        if (points.isEmpty()) appendPolyline(points, route.getPolyline());
        if (points.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "高德未返回赶赴路线坐标");
        return points;
    }

    private static void appendPolyline(List<List<Double>> points, String polyline) {
        if (polyline == null || polyline.isBlank()) return;
        try {
            for (String coordinate : polyline.split(";")) {
                String[] fields = coordinate.split(",");
                if (fields.length != 2) throw new NumberFormatException();
                double lon = Double.parseDouble(fields[0]);
                double lat = Double.parseDouble(fields[1]);
                if (!(lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90)) throw new NumberFormatException();
                append(points, List.of(lon, lat));
            }
        } catch (NumberFormatException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "高德返回了无效赶赴路线坐标");
        }
    }

    private static void append(List<List<Double>> points, List<Double> point) {
        if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) points.add(point);
    }

    private static String coordinate(List<Double> point) {
        return point.get(0) + "," + point.get(1);
    }
}
