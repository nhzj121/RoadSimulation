package org.example.roadsimulation.sandbox.cli;

import com.fasterxml.jackson.databind.*;
import org.example.roadsimulation.sandbox.optimization.*;
import org.example.roadsimulation.sandbox.execution.*;
import org.example.roadsimulation.sandbox.execution.bootstrap.SandboxControlledApplication;
import org.example.roadsimulation.sandbox.run.*;
import org.example.roadsimulation.sandbox.scenario.definition.*;
import org.springframework.core.io.DefaultResourceLoader;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Independent headless entry. Password is environment-only; existing prepare commands are explicit. */
public final class SandboxPlanExecutionCli {
    public record Run(PlanExecutionSummary summary, long startupNanos, long tickNanos, long ledgerWriteNanos,
                      long verificationNanos, int mapRequests) {}
    private SandboxPlanExecutionCli() {}
    public static void main(String[] args) throws Exception {
        var options=new HashMap<String,String>();
        for(String arg:args){int separator=arg.indexOf('=');if(!arg.startsWith("--")||separator<3)throw new IllegalArgumentException("Expected --name=value");
            String key=arg.substring(2,separator);
            if(!Set.of("task","jdbc-url","username","baseline","output").contains(key)||options.putIfAbsent(key,arg.substring(separator+1))!=null)
                throw new IllegalArgumentException("Unknown or duplicate option");}
        var task=OptimizationJson.read(Files.readString(Path.of(required(options.get("task"),"task"))),PlanExecutionTask.class);
        var summary=execute(task,required(options.getOrDefault("jdbc-url",System.getenv("SANDBOX_DB_URL")),"jdbc-url"),
                options.getOrDefault("username",System.getenv().getOrDefault("SANDBOX_DB_USER","road_sandbox_runtime")),
                required(System.getenv("SANDBOX_DB_PASSWORD"),"SANDBOX_DB_PASSWORD"),
                options.getOrDefault("baseline","classpath:sandbox/baseline/baseline-v1.json"));
        String text=OptimizationJson.write(summary);
        if(options.containsKey("output"))Files.writeString(Path.of(options.get("output")),text,StandardOpenOption.CREATE_NEW);
        System.out.println(text);
    }
    public static PlanExecutionSummary execute(PlanExecutionTask task,String url,String user,String password,String baselineLocation) throws Exception {
        var json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var baseline=new DefaultResourceLoader().getResource(baselineLocation);
        try(var lease=SandboxExecutionLease.acquire(url,user,password,baseline,task.runSpecKey(),task.runSpecRevision(),json)){
            var reference=lease.revision().specification().scenario();
            var scene=new SandboxScenarioStore(url,user,password,json).loadRevision(reference.scenarioKey(),reference.revision());
            var data=PlanExecutionAuthentication.verify(task,baseline,scene,lease.revision());
            return run(lease,task,data,url,user,password,baselineLocation,json,false, id -> {}).summary();
        }
    }

    static Run run(SandboxExecutionLease lease, PlanExecutionTask task,
            org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1.Data data, String url, String user,
            String password, String baselineLocation, ObjectMapper json, boolean offline, Consumer<String> onCreated) throws Exception {
        lease.createPlan(json,task);
        int[] completed={0};
        long[] writeNanos={0};
        try {
            onCreated.accept(lease.executionId());
            long starting = System.nanoTime();
            long startupNanos, tickNanos;
            PlanExecutionSummary summary;
            Map<String,Object> properties = Map.of("sandbox.baseline-resource", baselineLocation,
                    "sandbox.g3-offline", offline);
            try (var context = SandboxControlledApplication.open(url,user,password,properties)) {
                var runtime = context.getBean(PlanExecutionRuntime.class);
                runtime.install(task,data);
                lease.markRunning();
                var facts = context.getBean(SandboxRuntimeFactCollector.class);
                long tickStart = System.nanoTime();
                startupNanos = tickStart - starting;
                context.getBean(SandboxControlledRunExecutor.class).execute(lease,(tick,evaluation) -> {
                    long writeStart = System.nanoTime();
                    lease.recordTick(tick,evaluation,facts.capture(),json);
                    writeNanos[0] += System.nanoTime() - writeStart;
                    completed[0]++;
                });
                tickNanos = System.nanoTime() - tickStart - writeNanos[0];
                if (offline) context.getBean(OfflinePlanMaps.class).requireUnused();
                summary = PlanExecutionSummary.from(lease.executionId(),completed[0],runtime.state());
            }
            long verifying = System.nanoTime();
            new SandboxExecutionStore(url,user,password,json).verify(lease.executionId());
            lease.finish(completed[0],null);
            return new Run(summary,startupNanos,tickNanos,writeNanos[0],System.nanoTime()-verifying,0);
        } catch (Exception failure) {
            try { lease.finish(completed[0],failure); }
            catch (Exception close) { failure.addSuppressed(close); }
            throw failure;
        }
    }
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+name);return value;}
}
