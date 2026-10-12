package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.*;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV2;
import org.example.roadsimulation.sandbox.baseline.LexicographicJsonSha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.example.roadsimulation.dto.WeatherScenarioDTO.WeatherType.*;

@Service
public class WeatherEnvironmentService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.example.roadsimulation.repository.TransportExecutionSegmentRepository executionSegments;
    private final WeatherScenarioRepository scenarios;
    private final WeatherRunRepository runs;
    private final ObjectMapper json;
    private final SimulationContext clock;
    private final RandomEventProperties events;
    private final TransportRandomEventRepository eventRecords;
    private final DrivingProgressRepository progress;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private VehicleReplacementAttemptRepository replacementAttempts;
    private volatile WeatherRun currentRun;
    private volatile WeatherScenarioDTO currentScenario;
    private volatile FrozenTransportEventConfiguration currentEvents;
    private SandboxRunRuntimeContext sandboxRuntime;

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setSandboxRunRuntimeContext(SandboxRunRuntimeContext sandboxRuntime) {
        this.sandboxRuntime=Objects.requireNonNull(sandboxRuntime);
    }

    public WeatherEnvironmentService(WeatherScenarioRepository scenarios, WeatherRunRepository runs,
            ObjectMapper json, SimulationContext clock, RandomEventProperties events,
            TransportRandomEventRepository eventRecords, DrivingProgressRepository progress) {
        this.scenarios=scenarios; this.runs=runs; this.json=json; this.clock=clock;
        this.events=events; this.eventRecords=eventRecords; this.progress=progress;
    }

    public static WeatherScenarioDTO preset(WeatherScenarioDTO request) {
        if (request == null) throw new IllegalArgumentException("scenario required");
        WeatherScenarioDTO d = request;
        validateGeneration(d);
        String preset = Objects.requireNonNullElse(d.getPreset(), "DEMO");
        d.setTimeSlices(new ArrayList<>());
        d.setGeneratorVersion(WeatherTimelineV2.VERSION);
        d.setAfterTimeline("SUNNY");
        d.setSourceMode("AUTO".equals(preset) ? "SEEDED_GENERATION" : "EXPLICIT_TIMELINE");
        d.setBreakdownPolicy(new WeatherScenarioDTO.BreakdownPolicy("breakdown-v3",.6,30,60,30,60,60,120,.1,60,90));
        if (d.getName()==null || d.getName().isBlank()) d.setName(preset);
        switch (preset) {
            case "BASELINE" -> d.getTimeSlices().add(new WeatherScenarioDTO.TimeSlice(0,d.getGenerationHorizonMinutes(),SUNNY,1));
            case "DEMO" -> {
                addDemoSlice(d,0,60,SUNNY,1);
                addDemoSlice(d,60,180,RAIN,d.getSpeedFactors().get(RAIN));
                addDemoSlice(d,180,240,FOG,d.getSpeedFactors().get(FOG));
                addDemoSlice(d,240,d.getGenerationHorizonMinutes(),SUNNY,1);
            }
            case "AUTO" -> {
                d.setTimeSlices(new ArrayList<>(new WeatherTimelineV2(new ObjectMapper()).generate(
                        Long.toString(d.getSeed()), d.getGenerationIntervalMinutes(),
                        d.getGenerationHorizonMinutes(), d.getWeights(), d.getSpeedFactors())));
            }
            default -> throw new IllegalArgumentException("Unknown preset: " + preset);
        }
        return d;
    }

    private static void addDemoSlice(WeatherScenarioDTO d,long start,long end,
            WeatherScenarioDTO.WeatherType type,double factor) {
        long clippedEnd=Math.min(end,d.getGenerationHorizonMinutes());
        if(clippedEnd>start)d.getTimeSlices().add(new WeatherScenarioDTO.TimeSlice(start,clippedEnd,type,factor));
    }

    public static void validate(WeatherScenarioDTO d) {
        validate(d, false);
    }

    private static void validate(WeatherScenarioDTO d, boolean sandbox) {
        if(d==null || d.getName()==null || d.getName().isBlank() || d.getName().length()>200)
            throw new IllegalArgumentException("scenario name is required (max 200)");
        if (!"SEEDED_GENERATION".equals(d.getSourceMode()) && !"EXPLICIT_TIMELINE".equals(d.getSourceMode()))
            throw new IllegalArgumentException("Unknown weather sourceMode");
        if ("SEEDED_GENERATION".equals(d.getSourceMode())) validateGeneration(d);
        if(!(sandbox?"REJECT":"SUNNY").equals(d.getAfterTimeline()))
            throw new IllegalArgumentException("Unexpected afterTimeline policy");
        if(d.getGeneratorVersion()==null || d.getGeneratorVersion().isBlank()) throw new IllegalArgumentException("generatorVersion required");
        if(d.getTimeSlices()==null || d.getTimeSlices().isEmpty() || d.getTimeSlices().size()>10000)
            throw new IllegalArgumentException("1..10000 timeSlices required");
        long end=0;
        for(var s:d.getTimeSlices()) {
            if(s==null || s.weatherType()==null || s.startMinute()!=end || s.endMinute()<=end || s.endMinute()>525600
                    || !Double.isFinite(s.speedFactor()) || s.speedFactor()<=0 || s.speedFactor()>1)
                throw new IllegalArgumentException("timeSlices must be contiguous from zero, ordered, nonoverlapping; speedFactor in (0,1]");
            end=s.endMinute();
        }
    }

    private static void validateGeneration(WeatherScenarioDTO d) {
        if(d.getGenerationIntervalMinutes()<1 || d.getGenerationHorizonMinutes()<1 || d.getGenerationHorizonMinutes()>525600
                || Math.ceil(d.getGenerationHorizonMinutes()/(double)d.getGenerationIntervalMinutes())>10000)
            throw new IllegalArgumentException("Invalid generation interval or horizon");
        if(d.getWeights()==null || d.getWeights().size()!=4 || d.getWeights().stream().anyMatch(w->w==null || w<0 || w>10000)
                || d.getWeights().stream().mapToInt(Integer::intValue).sum()==0)
            throw new IllegalArgumentException("Four nonnegative weather weights with positive sum required");
        for(var type:WeatherScenarioDTO.WeatherType.values()) {
            Double speed=d.getSpeedFactors()==null?null:d.getSpeedFactors().get(type);
            if(speed==null || !Double.isFinite(speed) || speed<=0 || speed>1)
                throw new IllegalArgumentException("Weather speed factors must be in (0,1]");
        }
        // Legacy event fields remain importable but no longer control a weather run.
    }

    @Transactional public WeatherScenarioDTO create(WeatherScenarioDTO request, boolean imported) {
        WeatherScenarioDTO d=imported?request:preset(request);
        validate(d); d.setId(null);
        WeatherScenario entity=new WeatherScenario(); entity.setName(d.getName()); entity.setDefinitionJson(encode(d));
        entity=scenarios.save(entity); d.setId(entity.getId()); return d;
    }
    @Transactional(readOnly=true) public List<WeatherScenarioDTO> list() {return scenarios.findAll().stream().map(this::decode).toList();}
    @Transactional(readOnly=true) public WeatherScenarioDTO load(Long id) {
        return decode(scenarios.findById(id).orElseThrow(()->new IllegalArgumentException("Unknown scenario: "+id)));
    }
    private WeatherScenarioDTO decode(WeatherScenario entity) {
        try {var d=json.readValue(entity.getDefinitionJson(),WeatherScenarioDTO.class);d.setId(entity.getId());return d;}
        catch(Exception e){throw new IllegalStateException("Invalid stored weather scenario",e);}
    }
    public String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}

    @Transactional public synchronized void start(Long scenarioId,String externalExperimentId) {
        start(scenarioId,externalExperimentId,null);
    }
    public synchronized FrozenTransportEventConfiguration resolveEventOptions(TransportEventOptions options){
        var revision=sandboxRevision();
        var defaults=revision!=null?revision.eventConfiguration()
                :currentEvents==null?FrozenTransportEventConfiguration.snapshot(events):currentEvents;
        if(options==null)return defaults;
        var resolved=new FrozenTransportEventConfiguration(
                options.enabled()==null?defaults.enabled():options.enabled(),
                options.autoEnabled()==null?defaults.autoEnabled():options.autoEnabled(),
                options.rootSeed()==null?defaults.rootSeed():options.rootSeed(),
                options.congestion()==null?defaults.congestion():options.congestion(),
                options.breakdown()==null?defaults.breakdown():options.breakdown(),
                options.breakdownPolicy()==null?defaults.breakdownPolicy():options.breakdownPolicy());
        if(revision!=null&&!defaults.equals(resolved))
            throw new IllegalStateException("Sandbox events must match the published run revision");
        return resolved;
    }
    @Transactional public synchronized void start(Long scenarioId,String externalExperimentId,
            FrozenTransportEventConfiguration requestedEvents) {
        if(clock.isResetting()) throw new IllegalStateException("Simulation reset in progress");
        if(externalExperimentId!=null && externalExperimentId.length()>255) throw new IllegalArgumentException("externalExperimentId too long");
        var revision=sandboxRevision();
        if(revision!=null && (scenarioId!=null
                || requestedEvents!=null&&!requestedEvents.equals(revision.eventConfiguration())))
            throw new IllegalStateException("Sandbox weather and events must come from the published run revision");
        if(currentRun!=null) {
            if(scenarioId!=null && !scenarioId.equals(currentRun.getScenarioId())) throw new IllegalStateException("Scenario locked until reset");
            if(requestedEvents!=null&&!requestedEvents.equals(currentEvents))
                throw new IllegalStateException("Event configuration locked until reset");
            return;
        }
        if(clock.isRunning() || clock.getLoopCount()!=0) throw new IllegalStateException("Select scene before first start; reset to change it");
        WeatherScenarioDTO d;
        if(revision!=null){
            d=new WeatherScenarioDTO();
            d.setName("Sandbox "+revision.runSpecKey()+" revision "+revision.revision());
            d.setSourceMode("EXPLICIT_TIMELINE");
            d.setGeneratorVersion("sandbox-resolved-weather/v2");
            d.setTimeSlices(new ArrayList<>(revision.weatherTimeline()));
            d.setAfterTimeline("REJECT");
            d.setSeed(Long.parseLong(revision.specification().random().rootSeed()));
            d.setAutoEvents(revision.eventConfiguration().enabled()&&revision.eventConfiguration().autoEnabled());
            d.setCongestion(revision.eventConfiguration().congestion());
            d.setBreakdown(revision.eventConfiguration().breakdown());
            d.setBreakdownPolicy(revision.eventConfiguration().breakdownPolicy());
            WeatherTimelineV2.requireCoverage(d.getTimeSlices(),Math.multiplyExact(
                    revision.specification().simulationClock().tickDurationSeconds(),
                    revision.specification().simulationClock().totalLoops()));
        }else if(scenarioId==null){
            d=new WeatherScenarioDTO();d.setPreset("BASELINE");d.setName("Default clear weather");
            d.setGenerationHorizonMinutes(525600);preset(d);
        }else d=load(scenarioId);
        validate(d,revision!=null);
        FrozenTransportEventConfiguration frozenEvents=revision!=null?revision.eventConfiguration():requestedEvents==null
                ?FrozenTransportEventConfiguration.snapshot(events):requestedEvents;
        var hash=new LexicographicJsonSha256(json);
        String weatherHash=revision==null
                ?hash.hashObject(Map.of("timeSlices",d.getTimeSlices(),"afterTimeline",d.getAfterTimeline()))
                :hash.hashObject(Map.of("timeSlices",d.getTimeSlices(),"afterTimelinePolicy","REJECT"));
        if(revision!=null && (!weatherHash.equals(revision.fingerprints().weatherTimelineSha256())
                || !hash.hashObject(frozenEvents).equals(revision.fingerprints().eventConfigurationSha256())))
            throw new IllegalStateException("Published sandbox weather/event fingerprints do not match");
        String frozenSceneJson=encode(d);
        WeatherScenarioDTO frozenScene;
        try{frozenScene=json.readValue(frozenSceneJson,WeatherScenarioDTO.class);}
        catch(Exception e){throw new IllegalStateException("Cannot freeze weather scene",e);}
        WeatherRun run=new WeatherRun();run.setId(clock.beginRunIfAbsent());run.setScenarioId(scenarioId);
        run.setExternalExperimentId(externalExperimentId);run.setStartedAt(clock.getSimStart());
        run.setFrozenScenarioJson(frozenSceneJson);
        run.setFrozenEventConfigurationJson(encode(frozenEvents));
        run.setWeatherTimelineSha256(weatherHash);
        WeatherRun saved=runs.save(run);
        Runnable publish=()->{currentScenario=frozenScene;currentEvents=frozenEvents;currentRun=saved;};
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()){
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization(){
                        @Override public void afterCommit(){synchronized(WeatherEnvironmentService.this){publish.run();}}
                    });
        }else publish.run();
    }
    public String runId(){return currentRun==null?null:currentRun.getId();}
    public FrozenTransportEventConfiguration eventConfiguration(){return currentEvents;}
    public WeatherScenarioDTO.BreakdownPolicy breakdownPolicy(){return currentEvents==null?null:currentEvents.breakdownPolicy();}
    public WeatherCurrentDTO current(){return at(clock.getCurrentSimTime());}
    public synchronized WeatherCurrentDTO at(LocalDateTime time) {
        WeatherRun run=currentRun;WeatherScenarioDTO d=currentScenario;
        if(run==null || d==null) return new WeatherCurrentDTO(null,null,"SUNNY",1,null,events.isAutoEnabled(),false,clock.isRunning() || clock.getLoopCount()>0,time);
        long offset=Duration.between(run.getStartedAt(),time).getSeconds();
        var slice=sliceAt(d,offset);
        if(slice==null && "REJECT".equals(d.getAfterTimeline()))
            throw new IllegalStateException("Sandbox weather time is outside the published timeline");
        return new WeatherCurrentDTO(run.getScenarioId(),run.getId(),slice==null?"SUNNY":slice.weatherType().name(),slice==null?1:slice.speedFactor(),
                slice==null?null:run.getStartedAt().plusMinutes(slice.endMinute()),currentEvents.enabled()&&currentEvents.autoEnabled(),run.isManuallyIntervened(),true,time);
    }
    public static WeatherScenarioDTO.TimeSlice sliceAt(WeatherScenarioDTO d,long seconds) {
        return d.getTimeSlices().stream().filter(s->seconds>=s.startMinute()*60 && seconds<s.endMinute()*60).findFirst().orElse(null);
    }
    public synchronized List<LocalDateTime> boundaries(LocalDateTime from,LocalDateTime to) {
        if(currentRun==null)return List.of();
        requireTimelineWindow(from,to);
        return currentScenario.getTimeSlices().stream().map(s->currentRun.getStartedAt().plusMinutes(s.endMinute()))
                .filter(t->t.isAfter(from)&&t.isBefore(to)).toList();
    }
    public synchronized List<WeatherDrivingIntegrator.Window> windows(LocalDateTime from,LocalDateTime to){
        List<LocalDateTime> points=new ArrayList<>();points.add(from);points.addAll(boundaries(from,to));points.add(to);
        List<WeatherDrivingIntegrator.Window> result=new ArrayList<>();
        for(int i=0;i<points.size()-1;i++)result.add(new WeatherDrivingIntegrator.Window(
                points.get(i),points.get(i+1),at(points.get(i)).speedFactor()));
        return List.copyOf(result);
    }
    public double averageSpeedFactor(LocalDateTime from,LocalDateTime to){
        var windows=windows(from,to);
        double work=windows.stream().mapToDouble(w->w.seconds()*w.speedFactor()).sum();
        return work/Duration.between(from,to).getSeconds();
    }
    @Transactional public synchronized void manualIntervention(){
        if(currentRun!=null){
            WeatherRun updated=json.convertValue(currentRun,WeatherRun.class);
            updated.setManuallyIntervened(true);WeatherRun saved=runs.save(updated);
            publishAfterCommit(()->currentRun=saved);
        }
    }
    @Transactional public synchronized void archiveAndReset(LocalDateTime now) {
        if(currentRun!=null){
            // Build the archive separately: failed save/rollback must not mutate live run facts.
            WeatherRun archive=json.convertValue(currentRun,WeatherRun.class);
            archive.setEndedAt(now);archive.setEventHistoryJson(encode(eventRecords.findByRunId(archive.getId())));
            archive.setDrivingHistoryJson(encode(progress.findByRunId(archive.getId())));
            if(executionSegments!=null)archive.setExecutionSegmentHistoryJson(encode(
                    executionSegments.findByRunIdOrderByLegIdAscLoopIndexAscFragmentIndexAsc(archive.getId())));
            if(replacementAttempts!=null)archive.setReplacementAttemptHistoryJson(encode(replacementAttempts.findByRunId(archive.getId())));
            runs.save(archive);
        }
        Runnable clear=()->{currentRun=null;currentScenario=null;currentEvents=null;};
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization(){
                        @Override public void afterCommit(){synchronized(WeatherEnvironmentService.this){clear.run();}}
                    });
        else clear.run();
        progress.deleteAllInBatch();
    }
    private void publishAfterCommit(Runnable action) {
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization(){
                        @Override public void afterCommit(){synchronized(WeatherEnvironmentService.this){action.run();}}
                    });
        else action.run();
    }
    @Transactional(readOnly=true) public Map<String,Object> exportRun(String id) {
        WeatherRun run=runs.findById(id).orElseThrow(()->new IllegalArgumentException("Unknown run"));
        Map<String,Object> result=new LinkedHashMap<>();result.put("run",run);
        result.put("scenario",run.getFrozenScenarioJson()==null
                ? (run.getScenarioId()==null?null:load(run.getScenarioId())) : parse(run.getFrozenScenarioJson()));
        result.put("eventConfiguration",parse(run.getFrozenEventConfigurationJson()));
        result.put("drivingHistory",run.getEndedAt()==null?progress.findByRunId(id):parse(run.getDrivingHistoryJson()));
        result.put("events",run.getEndedAt()==null?eventRecords.findByRunId(id):parse(run.getEventHistoryJson()));
        result.put("executionSegments",run.getEndedAt()==null
                ?(executionSegments==null?List.of():executionSegments.findByRunIdOrderByLegIdAscLoopIndexAscFragmentIndexAsc(id))
                :parse(run.getExecutionSegmentHistoryJson()));
        result.put("replacementAttempts",run.getEndedAt()==null
                ?(replacementAttempts==null?List.of():replacementAttempts.findByRunId(id))
                :parse(run.getReplacementAttemptHistoryJson()));return result;
    }
    private Object parse(String s){try{return s==null?List.of():json.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}

    private SandboxRunSpecificationRevisionV2 sandboxRevision() {
        if(sandboxRuntime==null){
            if(clock.isDeterministicSandboxRun())
                throw new IllegalStateException("Sandbox runtime context is required; ordinary defaults are forbidden");
            return null;
        }
        if(!sandboxRuntime.isV2())throw new IllegalStateException("Sandbox weather requires a published v2 revision");
        return sandboxRuntime.revisionV2();
    }

    private void requireTimelineWindow(LocalDateTime from,LocalDateTime to) {
        if(currentRun==null || !"REJECT".equals(currentScenario.getAfterTimeline()))return;
        var slices=currentScenario.getTimeSlices();
        LocalDateTime end=currentRun.getStartedAt().plusMinutes(slices.get(slices.size()-1).endMinute());
        if(!to.isAfter(from) || from.isBefore(currentRun.getStartedAt()) || to.isAfter(end))
            throw new IllegalStateException("Sandbox weather window is outside the published timeline");
    }
}
