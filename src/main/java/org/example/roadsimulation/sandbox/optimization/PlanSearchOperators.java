package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Local changes to immutable plans. The validator remains authoritative for whole-plan legality. */
final class PlanSearchOperators {
    enum Kind { MOVE, SWAP, DEPARTURE, MERGE, SPLIT, REINSERT, CROSSOVER }
    record Proposal(GlobalPlan plan, List<String> rejections) {
        Proposal { rejections = List.copyOf(rejections); }
    }
    private record Placement(long vehicle, int index, BigDecimal delta) {}

    private final CompiledOptimizationProblem problem;
    private final SandboxBaselinePackageV1.Data data;
    private final GlobalPlanValidator validator = new GlobalPlanValidator();
    private final Map<Long, SandboxBaselinePackageV1.Vehicle> vehicles = new TreeMap<>();
    private final Map<GlobalPlan.TaskKey, ProductionTaskCatalog.Task> tasks = new HashMap<>();
    private final Map<ProductionTaskCatalog.FlowKey, ProductionTaskCatalog.Flow> flows = new HashMap<>();
    private final Map<List<String>, BigDecimal> meters = new HashMap<>();

    PlanSearchOperators(CompiledOptimizationProblem problem, SandboxBaselinePackageV1.Data data) {
        this.problem = problem;
        this.data = data;
        var driverVehicles = data.driverVehicleBindings().stream()
                .map(SandboxBaselinePackageV1.DriverVehicleBinding::vehicleId).collect(Collectors.toSet());
        data.vehicles().stream().filter(v -> driverVehicles.contains(v.id())).forEach(v -> vehicles.put(v.id(), v));
        problem.catalog().tasks().forEach(t -> tasks.put(t.key(), t));
        problem.catalog().flows().forEach(f -> flows.put(f.key(), f));
    }

    Proposal propose(Kind kind, GlobalPlan parent, PlanExecutionSummary summary, GlobalPlan donor,
                     PlanExecutionSummary donorSummary, SplittableRandom random) {
        var queues = queues(parent);
        var deferred = new ArrayList<>(parent.deferredTasks());
        if (kind == Kind.CROSSOVER) return crossover(parent, donor, donorSummary, random);
        boolean changed = switch (kind) {
            case MOVE -> move(queues, summary, random);
            case SWAP -> changeTrip(queues, summary, random, kind, key -> hasNext(queues, key));
            case DEPARTURE -> changeTrip(queues, summary, random, kind, key -> !departures(at(queues, key)).isEmpty());
            case MERGE -> changeTrip(queues, summary, random, kind, key -> canMerge(queues, key));
            case SPLIT -> changeTrip(queues, summary, random, kind, key -> loads(at(queues, key)).size() > 1);
            case REINSERT -> reinsert(queues, deferred, random);
            case CROSSOVER -> throw new IllegalStateException();
        };
        if (!changed) return new Proposal(null, List.of("NO_APPLICABLE_CHANGE"));
        var rejection = new ArrayList<String>();
        return new Proposal(checked(parent, queues, deferred, rejection), rejection);
    }

    private boolean move(TreeMap<Long, List<GlobalPlan.Trip>> queues, PlanExecutionSummary summary, SplittableRandom random) {
        var source = chooseTrip(queues, summary, key -> vehicles.size() > 1, random);
        if (source == null) return false;
        var trip = at(queues, source);
        var positions = placements(queues, trip, source.logicalVehicleId());
        if (positions.isEmpty()) return false;
        queues.get(source.logicalVehicleId()).remove(source.tripIndex());
        insert(queues, preferred(positions, random), trip);
        return true;
    }

    private boolean reinsert(TreeMap<Long, List<GlobalPlan.Trip>> queues, List<GlobalPlan.TaskKey> deferred,
                             SplittableRandom random) {
        if (deferred.isEmpty()) return false;
        var key = deferred.get(random.nextInt(deferred.size()));
        var flow = flows.get(tasks.get(key).flow());
        var trip = new GlobalPlan.Trip(key.generationLoop(), List.of(
                new GlobalPlan.Node(key, GlobalPlan.Action.LOAD, flow.originPoiId()),
                new GlobalPlan.Node(key, GlobalPlan.Action.UNLOAD, flow.destinationPoiId())));
        var positions = placements(queues, trip, -1);
        if (positions.isEmpty()) return false;
        insert(queues, preferred(positions, random), trip);
        deferred.remove(key);
        return true;
    }

    private boolean changeTrip(TreeMap<Long, List<GlobalPlan.Trip>> queues, PlanExecutionSummary summary,
                               SplittableRandom random, Kind kind, Predicate<TripKey> applicable) {
        var key = chooseTrip(queues, summary, applicable, random);
        if (key == null) return false;
        var queue = queues.get(key.logicalVehicleId());
        int index = key.tripIndex();
        var trip = queue.get(index);
        switch (kind) {
            case SWAP -> Collections.swap(queue, index, index + 1);
            case DEPARTURE -> {
                var slots = departures(trip);
                queue.set(index, new GlobalPlan.Trip(slots.get(random.nextInt(slots.size())), trip.nodes()));
            }
            case MERGE -> {
                var next = queue.remove(index + 1);
                var load = new ArrayList<>(loads(trip));
                load.addAll(loads(next));
                var nodes = new ArrayList<>(load);
                long delivery = lastPoi(trip);
                for (int i = load.size() - 1; i >= 0; i--)
                    nodes.add(new GlobalPlan.Node(load.get(i).task(), GlobalPlan.Action.UNLOAD, delivery));
                queue.set(index, new GlobalPlan.Trip(Math.max(trip.notBeforeLoop(), next.notBeforeLoop()), nodes));
            }
            case SPLIT -> {
                var load = loads(trip);
                int cut = 1 + random.nextInt(load.size() - 1);
                var first = load.subList(0, cut).stream().map(GlobalPlan.Node::task).collect(Collectors.toSet());
                queue.set(index, filterTrip(trip, first::contains));
                queue.add(index + 1, filterTrip(trip, Predicate.not(first::contains)));
            }
            default -> throw new IllegalArgumentException("Unsupported queue change");
        }
        return true;
    }

    private Proposal crossover(GlobalPlan parent, GlobalPlan donor, PlanExecutionSummary summary, SplittableRandom random) {
        if (donor == null) return new Proposal(null, List.of("NO_DISTINCT_DONOR"));
        var donorQueues = queues(donor);
        var key = chooseTrip(donorQueues, summary, ignored -> true, random);
        if (key == null) return new Proposal(null, List.of("NO_DONOR_TRIP"));
        return graft(parent, donor, key);
    }

    Proposal graft(GlobalPlan parent, GlobalPlan donor, TripKey key) {
        var trip = at(queues(donor), key);
        var moved = loads(trip).stream().map(GlobalPlan.Node::task).collect(Collectors.toSet());
        var base = queues(parent);
        for (var queue : base.values()) {
            queue.replaceAll(current -> filterTrip(current, Predicate.not(moved::contains)));
            queue.removeIf(Objects::isNull);
        }
        var deferred = parent.deferredTasks().stream().filter(Predicate.not(moved::contains)).toList();
        int size = base.getOrDefault(key.logicalVehicleId(), List.of()).size();
        int original = Math.min(key.tripIndex(), size);
        var rejection = new ArrayList<String>();
        for (int index : List.of(original, original - 1, original + 1)) {
            if (index < 0 || index > size) continue;
            var candidate = new TreeMap<>(base);
            candidate.put(key.logicalVehicleId(), new ArrayList<>(base.getOrDefault(key.logicalVehicleId(), List.of())));
            candidate.get(key.logicalVehicleId()).add(index, trip);
            var plan = checked(parent, candidate, deferred, rejection);
            if (plan != null) return new Proposal(plan, rejection);
        }
        return new Proposal(null, rejection);
    }

    private GlobalPlan checked(GlobalPlan parent, TreeMap<Long, List<GlobalPlan.Trip>> queues,
                               List<GlobalPlan.TaskKey> deferred, List<String> rejections) {
        try {
            var assignments = queues.entrySet().stream().filter(e -> !e.getValue().isEmpty())
                    .map(e -> new GlobalPlan.VehiclePlan(e.getKey(), e.getValue())).toList();
            var plan = new GlobalPlan(GlobalPlan.VERSION, parent.problem(), parent.catalogTaskKeys(), assignments, deferred);
            if (parent.equals(plan)) {
                rejections.add("UNCHANGED_PLAN");
                return null;
            }
            validator.validatePlan(problem, plan, data);
            return plan;
        } catch (IllegalArgumentException invalid) {
            rejections.add(invalid.getMessage());
            return null;
        }
    }

    static List<Integer> departureSlots(int notBefore, int horizon) {
        long current = Math.max(3, (notBefore + 2L) / 3 * 3);
        var slots = new ArrayList<Integer>(2);
        for (long slot : List.of(current - 3, current + 3))
            if (slot > 0 && slot < horizon) slots.add((int) slot);
        return slots;
    }

    private List<Integer> departures(GlobalPlan.Trip trip) {
        return departureSlots(trip.notBeforeLoop(), problem.problem().totalLoops());
    }

    private TripKey chooseTrip(TreeMap<Long, List<GlobalPlan.Trip>> queues, PlanExecutionSummary summary,
                              Predicate<TripKey> applicable, SplittableRandom random) {
        var dispatched = new HashSet<TripKey>();
        var waitingHeads = new HashSet<TripKey>();
        if (summary != null) {
            summary.state().bindings().forEach(b -> dispatched.add(b.trip()));
            summary.state().queues().forEach(q -> waitingHeads.add(new TripKey(q.logicalVehicleId(), q.nextTripIndex())));
        }
        var keys = new ArrayList<TripKey>();
        queues.forEach((vehicle, queue) -> {
            for (int i = 0; i < queue.size(); i++) {
                var key = new TripKey(vehicle, i);
                if (applicable.test(key)) keys.add(key);
            }
        });
        keys.sort(Comparator.comparingInt((TripKey key) -> dispatched.contains(key) ? 0 : waitingHeads.contains(key) ? 1 : 2)
                .thenComparing(key -> pickupMeters(queues, key), Comparator.reverseOrder())
                .thenComparing(Comparator.naturalOrder()));
        return keys.isEmpty() ? null : preferred(keys, random);
    }

    private List<Placement> placements(TreeMap<Long, List<GlobalPlan.Trip>> queues, GlobalPlan.Trip trip, long excluded) {
        var result = new ArrayList<Placement>();
        vehicles.forEach((id, vehicle) -> {
            if (id == excluded || !fits(vehicle, trip)) return;
            var queue = queues.getOrDefault(id, List.of());
            for (int index = 0; index <= queue.size(); index++) {
                String from = tail(id, queue, index);
                var delta = distance(from, poi(firstPoi(trip)));
                if (index < queue.size()) {
                    String next = poi(firstPoi(queue.get(index)));
                    delta = delta.add(distance(poi(lastPoi(trip)), next)).subtract(distance(from, next));
                }
                result.add(new Placement(id, index, delta));
            }
        });
        result.sort(Comparator.comparing(Placement::delta).thenComparingLong(Placement::vehicle).thenComparingInt(Placement::index));
        return result;
    }

    private boolean fits(SandboxBaselinePackageV1.Vehicle vehicle, GlobalPlan.Trip trip) {
        BigDecimal weight = BigDecimal.ZERO, volume = BigDecimal.ZERO;
        for (var node : trip.nodes()) {
            var task = tasks.get(node.task());
            if (node.action() == GlobalPlan.Action.LOAD) {
                weight = weight.add(task.weightTonnes());
                volume = volume.add(task.volumeCubicMeters());
                if (!GlobalPlanValidator.fits(vehicle, weight, volume)) return false;
            } else {
                weight = weight.subtract(task.weightTonnes());
                volume = volume.subtract(task.volumeCubicMeters());
            }
        }
        return true;
    }

    private BigDecimal pickupMeters(Map<Long, List<GlobalPlan.Trip>> queues, TripKey key) {
        var queue = queues.get(key.logicalVehicleId());
        return distance(tail(key.logicalVehicleId(), queue, key.tripIndex()), poi(firstPoi(queue.get(key.tripIndex()))));
    }

    private BigDecimal distance(String from, String to) {
        return meters.computeIfAbsent(List.of(from, to), key -> problem.paths().between(from, to).distanceMeters());
    }

    private static boolean canMerge(Map<Long, List<GlobalPlan.Trip>> queues, TripKey key) {
        if (!hasNext(queues, key)) return false;
        var a = at(queues, key);
        var b = queues.get(key.logicalVehicleId()).get(key.tripIndex() + 1);
        if (firstPoi(a) != firstPoi(b) || lastPoi(a) != lastPoi(b)) return false;
        return java.util.stream.Stream.concat(a.nodes().stream(), b.nodes().stream()).allMatch(node ->
                node.poiId() == (node.action() == GlobalPlan.Action.LOAD ? firstPoi(a) : lastPoi(a)));
    }

    private static boolean hasNext(Map<Long, List<GlobalPlan.Trip>> queues, TripKey key) {
        return key.tripIndex() + 1 < queues.get(key.logicalVehicleId()).size();
    }

    private static GlobalPlan.Trip filterTrip(GlobalPlan.Trip trip, Predicate<GlobalPlan.TaskKey> keep) {
        var nodes = trip.nodes().stream().filter(node -> keep.test(node.task())).toList();
        return nodes.isEmpty() ? null : new GlobalPlan.Trip(trip.notBeforeLoop(), nodes);
    }

    private static List<GlobalPlan.Node> loads(GlobalPlan.Trip trip) {
        return trip.nodes().stream().filter(n -> n.action() == GlobalPlan.Action.LOAD).toList();
    }

    private static TreeMap<Long, List<GlobalPlan.Trip>> queues(GlobalPlan plan) {
        var queues = new TreeMap<Long, List<GlobalPlan.Trip>>();
        plan.vehicles().forEach(vehicle -> queues.put(vehicle.vehicleId(), new ArrayList<>(vehicle.trips())));
        return queues;
    }

    private static GlobalPlan.Trip at(Map<Long, List<GlobalPlan.Trip>> queues, TripKey key) {
        return queues.get(key.logicalVehicleId()).get(key.tripIndex());
    }

    private static void insert(Map<Long, List<GlobalPlan.Trip>> queues, Placement position, GlobalPlan.Trip trip) {
        queues.computeIfAbsent(position.vehicle, unused -> new ArrayList<>()).add(position.index, trip);
    }

    private static String tail(long vehicle, List<GlobalPlan.Trip> queue, int index) {
        return index == 0 ? "VEHICLE_START:" + vehicle : poi(lastPoi(queue.get(index - 1)));
    }
    private static String poi(long id) { return "POI:" + id; }
    private static long firstPoi(GlobalPlan.Trip trip) { return trip.nodes().get(0).poiId(); }
    private static long lastPoi(GlobalPlan.Trip trip) { return trip.nodes().get(trip.nodes().size() - 1).poiId(); }

    private static <T> T preferred(List<T> options, SplittableRandom random) {
        int window = random.nextInt(4) == 0 ? options.size() : Math.min(3, options.size());
        return options.get(random.nextInt(window));
    }
}
