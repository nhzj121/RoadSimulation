package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Offline construction. Nominal times rank options; the executor alone determines actual release and dispatch. */
public final class GreedyBaseline {
    public static final String VERSION = "greedy-baseline/v1";
    public enum Reason { NO_COMPATIBLE_VEHICLE, UPSTREAM_DEFERRED }
    public record Deferral(GlobalPlan.TaskKey task, Reason reason) {}
    public record Result(String artifactVersion, GlobalPlan plan, List<Deferral> deferrals) {
        public Result { deferrals = List.copyOf(deferrals); }
    }

    private record Ranked(ProductionTaskCatalog.Task task, int depth, long origin, long destination) {
        GlobalPlan.TaskKey key() { return task.key(); }
        String pickup() { return "POI:" + origin; }
        String delivery() { return "POI:" + destination; }
    }
    private record Group(int generation, int depth, long origin, long destination) {}
    private static final class Queue {
        final SandboxBaselinePackageV1.Vehicle vehicle;
        final List<GlobalPlan.Trip> trips = new ArrayList<>();
        String tail;
        long end;

        Queue(SandboxBaselinePackageV1.Vehicle vehicle) {
            this.vehicle = vehicle;
            tail = "VEHICLE_START:" + vehicle.id();
        }
    }
    private record Option(Queue queue, List<Ranked> load, long end, BigDecimal emptyMeters, BigDecimal weight) {}
    private static final Comparator<Option> OPTIONS = Comparator.comparingLong(Option::end)
            .thenComparing(Option::emptyMeters)
            .thenComparing(Option::weight, Comparator.reverseOrder())
            .thenComparingLong(option -> option.queue.vehicle.id());

    public Result build(CompiledOptimizationProblem problem, SandboxBaselinePackageV1.Data data) {
        var catalog = problem.catalog();
        var flows = index(catalog.flows(), ProductionTaskCatalog.Flow::key);
        var stages = index(problem.dependencies().stages(), ProductionDependencies.Node::stage);
        var depths = depths(stages);
        var drivers = data.driverVehicleBindings().stream().map(SandboxBaselinePackageV1.DriverVehicleBinding::vehicleId)
                .collect(Collectors.toSet());
        var queues = data.vehicles().stream().filter(vehicle -> drivers.contains(vehicle.id())).map(Queue::new).toList();
        var tasks = catalog.tasks().stream().map(task -> {
            var flow = flows.get(task.flow());
            return new Ranked(task, depths.get(producer(task.key())), flow.originPoiId(), flow.destinationPoiId());
        }).sorted(Comparator.comparingInt((Ranked task) -> task.key().generationLoop())
                .thenComparingInt(Ranked::depth).thenComparing(task -> OptimizationJson.write(task.key()))).toList();

        var groups = new HashMap<Group, List<Ranked>>();
        tasks.forEach(task -> groups.computeIfAbsent(group(task), unused -> new ArrayList<>()).add(task));
        var deferred = new LinkedHashMap<GlobalPlan.TaskKey, Reason>();
        var arranged = new HashSet<GlobalPlan.TaskKey>();
        var routes = new HashMap<List<String>, SphericalPathModel.Leg>();
        for (var first : tasks) {
            if (arranged.contains(first.key())) continue;
            if (blocked(first, stages, deferred)) {
                deferred.put(first.key(), Reason.UPSTREAM_DEFERRED);
                continue;
            }
            var sameRoute = groups.get(group(first)).stream()
                    .filter(task -> !task.key().equals(first.key()) && !arranged.contains(task.key())
                            && !deferred.containsKey(task.key()) && !blocked(task, stages, deferred)).toList();
            var best = queues.stream().filter(queue -> fits(queue, first.task.weightTonnes(), first.task.volumeCubicMeters()))
                    .map(queue -> option(problem, routes, queue, first, sameRoute)).min(OPTIONS);
            if (best.isEmpty()) {
                deferred.put(first.key(), Reason.NO_COMPATIBLE_VEHICLE);
                continue;
            }
            var selected = best.orElseThrow();
            selected.queue.trips.add(trip(first, selected.load));
            selected.queue.tail = first.delivery();
            selected.queue.end = selected.end;
            selected.load.forEach(task -> arranged.add(task.key()));
        }
        var vehicles = queues.stream().filter(queue -> !queue.trips.isEmpty())
                .map(queue -> new GlobalPlan.VehiclePlan(queue.vehicle.id(), queue.trips)).toList();
        var plan = new GlobalPlan(GlobalPlan.VERSION, problem.problem(), catalog.taskKeys(), vehicles,
                List.copyOf(deferred.keySet()));
        new GlobalPlanValidator().validatePlan(problem, plan, data);
        return new Result(VERSION, plan, deferred.entrySet().stream()
                .map(entry -> new Deferral(entry.getKey(), entry.getValue())).toList());
    }

    private static Option option(CompiledOptimizationProblem problem, Map<List<String>, SphericalPathModel.Leg> routes,
                                 Queue queue, Ranked first, List<Ranked> sameRoute) {
        var load = new ArrayList<Ranked>();
        load.add(first);
        BigDecimal weight = first.task.weightTonnes(), volume = first.task.volumeCubicMeters();
        for (var task : sameRoute) {
            var nextWeight = weight.add(task.task.weightTonnes());
            var nextVolume = volume.add(task.task.volumeCubicMeters());
            if (!fits(queue, nextWeight, nextVolume)) continue;
            load.add(task);
            weight = nextWeight;
            volume = nextVolume;
        }
        long tick = problem.problem().tickSeconds();
        long earliestLoop = Math.max(first.key().generationLoop(), (queue.end + tick - 1) / tick);
        long dispatchLoop = Math.max(3, (earliestLoop + 2) / 3 * 3);
        var empty = leg(problem.paths(), routes, queue.tail, first.pickup());
        var loaded = leg(problem.paths(), routes, first.pickup(), first.delivery());
        return new Option(queue, load, dispatchLoop * tick + Math.max(1, empty.drivingSeconds() + loaded.drivingSeconds()),
                empty.distanceMeters(), weight);
    }

    private static SphericalPathModel.Leg leg(SphericalPathModel paths, Map<List<String>, SphericalPathModel.Leg> routes,
                                               String from, String to) {
        return routes.computeIfAbsent(List.of(from, to), key -> paths.between(from, to));
    }

    private static GlobalPlan.Trip trip(Ranked first, List<Ranked> load) {
        var nodes = new ArrayList<GlobalPlan.Node>();
        load.forEach(task -> nodes.add(new GlobalPlan.Node(task.key(), GlobalPlan.Action.LOAD, first.origin)));
        for (int index = load.size() - 1; index >= 0; index--)
            nodes.add(new GlobalPlan.Node(load.get(index).key(), GlobalPlan.Action.UNLOAD, first.destination));
        return new GlobalPlan.Trip(first.key().generationLoop(), nodes);
    }

    private static boolean fits(Queue queue, BigDecimal weight, BigDecimal volume) {
        return GlobalPlanValidator.fits(queue.vehicle, weight, volume);
    }

    private static Group group(Ranked task) {
        return new Group(task.key().generationLoop(), task.depth, task.origin, task.destination);
    }

    private static boolean blocked(Ranked task, Map<ProductionTaskCatalog.StageKey, ProductionDependencies.Node> stages,
                                   Map<GlobalPlan.TaskKey, Reason> deferred) {
        return stages.get(producer(task.key())).inbound().stream().anyMatch(deferred::containsKey);
    }

    private static ProductionTaskCatalog.StageKey producer(GlobalPlan.TaskKey task) {
        return new ProductionTaskCatalog.StageKey(new ProductionTaskCatalog.BatchKey(task.generationLoop(), task.chainId()),
                task.fromStageId());
    }

    private static Map<ProductionTaskCatalog.StageKey, Integer> depths(
            Map<ProductionTaskCatalog.StageKey, ProductionDependencies.Node> stages) {
        var depths = new HashMap<ProductionTaskCatalog.StageKey, Integer>();
        var pending = new HashMap<ProductionTaskCatalog.StageKey, Integer>();
        var ready = new ArrayDeque<ProductionTaskCatalog.StageKey>();
        stages.forEach((key, node) -> {
            pending.put(key, node.inbound().size());
            if (node.inbound().isEmpty()) { depths.put(key, 0); ready.add(key); }
        });
        while (!ready.isEmpty()) {
            var stage = ready.remove();
            for (var task : stages.get(stage).outbound()) {
                var target = new ProductionTaskCatalog.StageKey(stage.batch(), task.toStageId());
                depths.merge(target, depths.get(stage) + 1, Math::max);
                if (pending.merge(target, -1, Integer::sum) == 0) ready.add(target);
            }
        }
        return depths;
    }

    private static <K, V> Map<K, V> index(List<V> values, Function<V, K> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
