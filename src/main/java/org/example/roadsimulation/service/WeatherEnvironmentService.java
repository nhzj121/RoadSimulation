package org.example.roadsimulation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.config.RandomEventProperties;
import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.dto.*;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.example.roadsimulation.dto.WeatherScenarioDTO.WeatherType.*;

@Service
public class WeatherEnvironmentService {
    private final WeatherScenarioRepository scenarios;
    private final WeatherRunRepository runs;
    private final ObjectMapper json;
    private final SimulationContext clock;
    private final RandomEventProperties events;
    private final TransportRandomEventRepository eventRecords;
    private final DrivingProgressRepository progress;
    private volatile WeatherRun currentRun;
    private volatile WeatherScenarioDTO currentScenario;
    private Boolean originalAuto;
    private Long originalSeed;
    private WeatherScenarioDTO.EventParameters originalCongestion;
    private WeatherScenarioDTO.EventParameters originalBreakdown;

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
        d.setGeneratorVersion("weather-v1");
        d.setAfterTimeline("SUNNY");
        d.setAutoEvents("AUTO".equals(preset));
        if (d.getName()==null || d.getName().isBlank()) d.setName(preset);
        switch (preset) {
            case "BASELINE" -> d.getTimeSlices().add(new WeatherScenarioDTO.TimeSlice(0,1440,SUNNY,1));
            case "DEMO" -> {
                d.getTimeSlices().add(new WeatherScenarioDTO.TimeSlice(0,60,SUNNY,1));
                d.getTimeSlices().add(new WeatherScenarioDTO.TimeSlice(60,180,RAIN,d.getSpeedFactors().get(RAIN)));
                d.getTimeSlices().add(new WeatherScenarioDTO.TimeSlice(180,240,FOG,d.getSpeedFactors().get(FOG)));
                d.getTimeSlices().add(new WeatherScenarioDTO.TimeSlice(240,1440,SUNNY,1));
            }
            case "AUTO" -> {
                SplittableRandom random = new SplittableRandom(d.getSeed() ^ 0x57454154484552L);
                int total=d.getWeights().stream().mapToInt(Integer::intValue).sum();
                for(int t=0;t<d.getGenerationHorizonMinutes();t+=d.getGenerationIntervalMinutes()) {
                    int n=random.nextInt(total), index=0;
                    while(index<3 && n>=d.getWeights().get(index)) n-=d.getWeights().get(index++);
                    var type=WeatherScenarioDTO.WeatherType.values()[index];
                    d.getTimeSlices().add(new WeatherScenarioDTO.TimeSlice(t,
                            Math.min(t+d.getGenerationIntervalMinutes(),d.getGenerationHorizonMinutes()),type,d.getSpeedFactors().get(type)));
                }
            }
            default -> throw new IllegalArgumentException("Unknown preset: " + preset);
        }
        return d;
    }

    public static void validate(WeatherScenarioDTO d) {
        if(d==null || d.getName()==null || d.getName().isBlank() || d.getName().length()>200)
            throw new IllegalArgumentException("scenario name is required (max 200)");
        validateGeneration(d);
        if(!"SUNNY".equals(d.getAfterTimeline())) throw new IllegalArgumentException("afterTimeline must be SUNNY");
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
        validateEvent(d.getCongestion(),false); validateEvent(d.getBreakdown(),true);
    }

    private static void validateEvent(WeatherScenarioDTO.EventParameters p,boolean breakdown) {
        if(p==null || !Double.isFinite(p.hourlyProbability()) || p.hourlyProbability()<0 || p.hourlyProbability()>1
                || p.minDurationMinutes()<1 || p.maxDurationMinutes()<p.minDurationMinutes() || p.maxDurationMinutes()>1440
                || !Double.isFinite(p.speedFactor()) || p.speedFactor()<0 || p.speedFactor()>1
                || (breakdown && p.speedFactor()!=0) || (!breakdown && p.speedFactor()<=0))
            throw new IllegalArgumentException("Invalid single vehicle event parameters");
    }

    private static WeatherScenarioDTO.EventParameters snapshot(RandomEventProperties.EventSettings s) {
        return new WeatherScenarioDTO.EventParameters(s.getHourlyProbability(),s.getMinDurationMinutes(),s.getMaxDurationMinutes(),s.getSpeedFactor());
    }
    private static void apply(RandomEventProperties.EventSettings s,WeatherScenarioDTO.EventParameters p) {
        s.setHourlyProbability(p.hourlyProbability());s.setMinDurationMinutes(p.minDurationMinutes());
        s.setMaxDurationMinutes(p.maxDurationMinutes());s.setSpeedFactor(p.speedFactor());
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
        if(clock.isResetting()) throw new IllegalStateException("Simulation reset in progress");
        if(externalExperimentId!=null && externalExperimentId.length()>255) throw new IllegalArgumentException("externalExperimentId too long");
        if(currentRun!=null) {
            if(scenarioId!=null && !scenarioId.equals(currentRun.getScenarioId())) throw new IllegalStateException("Scenario locked until reset");
            return;
        }
        if(scenarioId==null) return;
        if(clock.isRunning() || clock.getLoopCount()!=0) throw new IllegalStateException("Select scene before first start; reset to change it");
        WeatherScenarioDTO d=load(scenarioId);
        validate(d);
        WeatherRun run=new WeatherRun();run.setId(UUID.randomUUID().toString());run.setScenarioId(scenarioId);
        run.setExternalExperimentId(externalExperimentId);run.setStartedAt(clock.getSimStart());
        currentRun=runs.save(run);currentScenario=d;
        originalAuto=events.isAutoEnabled();events.setAutoEnabled(d.isAutoEvents());
        originalSeed=events.getSeed();events.setSeed(d.getSeed());
        originalCongestion=snapshot(events.getCongestion());originalBreakdown=snapshot(events.getBreakdown());
        apply(events.getCongestion(),d.getCongestion());apply(events.getBreakdown(),d.getBreakdown());
    }
    public String runId(){return currentRun==null?null:currentRun.getId();}
    public WeatherCurrentDTO current(){return at(clock.getCurrentSimTime());}
    public synchronized WeatherCurrentDTO at(LocalDateTime time) {
        WeatherRun run=currentRun;WeatherScenarioDTO d=currentScenario;
        if(run==null || d==null) return new WeatherCurrentDTO(null,null,"SUNNY",1,null,events.isAutoEnabled(),false,clock.isRunning() || clock.getLoopCount()>0,time);
        long offset=Duration.between(run.getStartedAt(),time).getSeconds();
        var slice=sliceAt(d,offset);
        return new WeatherCurrentDTO(run.getScenarioId(),run.getId(),slice==null?"SUNNY":slice.weatherType().name(),slice==null?1:slice.speedFactor(),
                slice==null?null:run.getStartedAt().plusMinutes(slice.endMinute()),events.isAutoEnabled(),run.isManuallyIntervened(),true,time);
    }
    public static WeatherScenarioDTO.TimeSlice sliceAt(WeatherScenarioDTO d,long seconds) {
        return d.getTimeSlices().stream().filter(s->seconds>=s.startMinute()*60 && seconds<s.endMinute()*60).findFirst().orElse(null);
    }
    public synchronized List<LocalDateTime> boundaries(LocalDateTime from,LocalDateTime to) {
        if(currentRun==null)return List.of();
        return currentScenario.getTimeSlices().stream().map(s->currentRun.getStartedAt().plusMinutes(s.endMinute()))
                .filter(t->t.isAfter(from)&&t.isBefore(to)).toList();
    }
    @Transactional public synchronized void manualIntervention(){if(currentRun!=null){currentRun.setManuallyIntervened(true);runs.save(currentRun);}}
    @Transactional public synchronized void archiveAndReset(LocalDateTime now) {
        if(currentRun!=null){
            currentRun.setEndedAt(now);currentRun.setEventHistoryJson(encode(eventRecords.findByRunId(currentRun.getId())));
            currentRun.setDrivingHistoryJson(encode(progress.findByRunId(currentRun.getId())));runs.save(currentRun);
        }
        currentRun=null;currentScenario=null;
        if(originalAuto!=null){events.setAutoEnabled(originalAuto);originalAuto=null;}
        if(originalSeed!=null){events.setSeed(originalSeed);originalSeed=null;}
        if(originalCongestion!=null){apply(events.getCongestion(),originalCongestion);originalCongestion=null;}
        if(originalBreakdown!=null){apply(events.getBreakdown(),originalBreakdown);originalBreakdown=null;}
        progress.deleteAllInBatch();
    }
    @Transactional(readOnly=true) public Map<String,Object> exportRun(String id) {
        WeatherRun run=runs.findById(id).orElseThrow(()->new IllegalArgumentException("Unknown run"));
        Map<String,Object> result=new LinkedHashMap<>();result.put("run",run);result.put("scenario",load(run.getScenarioId()));
        result.put("drivingHistory",run.getEndedAt()==null?progress.findByRunId(id):parse(run.getDrivingHistoryJson()));
        result.put("events",run.getEndedAt()==null?eventRecords.findByRunId(id):parse(run.getEventHistoryJson()));return result;
    }
    private Object parse(String s){try{return s==null?List.of():json.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
}
