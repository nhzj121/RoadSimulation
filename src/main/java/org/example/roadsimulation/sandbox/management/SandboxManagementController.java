package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sandbox")
@Profile("!sandbox-runtime & !sandbox-controlled-runtime")
@ConditionalOnProperty(name = "sandbox.management.enabled", havingValue = "true")
public class SandboxManagementController {
    private final SandboxManagementService service;
    private final SandboxJobCoordinator coordinator;
    public SandboxManagementController(SandboxManagementService service, SandboxJobCoordinator coordinator) { this.service = service; this.coordinator = coordinator; }
    @GetMapping("/baseline") public Object baseline() { return service.baselineInfo(); }
    @GetMapping("/baseline/{type}") public Object catalog(@PathVariable String type, @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="50") int limit) { return service.catalog(type, offset, limit); }
    @GetMapping("/workspace") public Object workspace() { return service.workspace(); }
    @GetMapping("/templates/scenario") public Object scenarioTemplate() { return service.scenarioTemplate(); }
    @GetMapping("/templates/run") public Object runTemplate(@RequestParam String scenarioKey, @RequestParam int revision) { return service.runTemplate(scenarioKey, revision); }
    @PostMapping(value="/scenarios/compile", consumes=MediaType.APPLICATION_JSON_VALUE) public Object compileScenario(@RequestBody String body) { return service.compileScenario(service.definition(body)); }
    @PostMapping(value="/scenarios/draft", consumes=MediaType.APPLICATION_JSON_VALUE) public Object saveScenario(@RequestBody String body) { return service.saveScenario(service.definition(body)); }
    @PostMapping("/scenarios/{key}/publish") public Object publishScenario(@PathVariable String key) { return service.publishScenario(key); }
    @GetMapping("/scenarios/{key}/revisions/{revision}") public Object scenarioRevision(@PathVariable String key, @PathVariable int revision) { return service.scenarioRevision(key, revision); }
    @PostMapping(value="/runs/compile", consumes=MediaType.APPLICATION_JSON_VALUE) public Object compileRun(@RequestBody String body) { return service.compileRun(service.definition(body)); }
    @PostMapping(value="/runs/draft", consumes=MediaType.APPLICATION_JSON_VALUE) public Object saveRun(@RequestBody String body) { return service.saveRun(service.definition(body)); }
    @PostMapping("/runs/{key}/publish") public Object publishRun(@PathVariable String key) { return service.publishRun(key); }
    @GetMapping("/runs/{key}/revisions/{revision}") public Object runRevision(@PathVariable String key, @PathVariable int revision) { return service.runRevision(key, revision); }
    @GetMapping("/{kind:scenarios|runs}") public Object families(@PathVariable String kind, @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="50") int limit) { return service.families(kind, offset, limit); }
    @GetMapping("/{kind:scenarios|runs}/{key}/draft") public Object draft(@PathVariable String kind, @PathVariable String key) { return service.draft(kind, key); }
    @GetMapping("/{kind:scenarios|runs}/{key}/draft-view") public Object draftView(@PathVariable String kind, @PathVariable String key) { return service.draftView(kind, key); }
    @PostMapping(value="/{kind:scenarios|runs}/guarded-draft", consumes=MediaType.APPLICATION_JSON_VALUE)
    public Object guardedSave(@PathVariable String kind, @RequestBody String body) { return service.guardedSave(kind, service.definition(body)); }
    @PostMapping(value="/{kind:scenarios|runs}/{key}/guarded-publish", consumes=MediaType.APPLICATION_JSON_VALUE)
    public Object guardedPublish(@PathVariable String kind, @PathVariable String key, @RequestBody String body) { return service.guardedPublish(kind, key, service.definition(body)); }
    @GetMapping("/{kind:scenarios|runs}/{key}/revisions") public Object revisions(@PathVariable String kind, @PathVariable String key, @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="50") int limit) { return service.revisions(kind, key, offset, limit); }
    @PostMapping(value="/jobs", consumes=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<?> submit(@RequestBody String body) {
        JsonNode request = service.definition(body);
        if (request.size() != 2 || !request.path("runSpecKey").isTextual() || !request.path("revision").isIntegralNumber()
                || !request.path("revision").canConvertToInt()) throw new org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException("INVALID_ARGUMENT", "Only runSpecKey and revision are accepted");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(coordinator.submit(request.path("runSpecKey").asText(), request.path("revision").asInt()));
    }
    @GetMapping("/jobs") public Object jobs(@RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="50") int limit) { return service.jobs(offset, limit); }
    @PostMapping(value="/start-requests", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> start(@RequestBody String body) {
        JsonNode request = service.definition(body);
        if (request.size() != 3 || !request.path("runSpecKey").isTextual() || !request.path("revision").isIntegralNumber()
                || !request.path("revision").canConvertToInt() || !request.path("clientRequestId").isTextual())
            throw new org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException("INVALID_ARGUMENT", "Published revision and request UUID required");
        var receipt = coordinator.submitRequest(request.path("runSpecKey").asText(), request.path("revision").asInt(), request.path("clientRequestId").asText());
        return ResponseEntity.status(receipt.reused() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(receipt);
    }
    @GetMapping("/start-requests/{id}") public Object startRequest(@PathVariable String id) { return service.startRequest(id); }
    @GetMapping("/jobs/{id}") public Object job(@PathVariable String id) { return service.job(id); }
    @GetMapping("/jobs/{id}/view") public Object jobView(@PathVariable String id) { return service.jobView(id); }
    @PostMapping("/jobs/{id}/cancel") public Object cancel(@PathVariable String id) { return service.cancel(id); }
    @GetMapping("/executions") public Object executions(@RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="50") int limit) { return service.executions(offset, limit); }
    @GetMapping("/executions/{id}") public Object execution(@PathVariable String id) { return service.execution(id); }
    @GetMapping("/executions/{id}/summary") public Object executionSummary(@PathVariable String id) { return service.executionSummary(id); }
    @GetMapping("/executions/{id}/configuration") public Object configuration(@PathVariable String id) { return service.executionConfiguration(id); }
    @GetMapping("/executions/{id}/map-context") public Object mapContext(@PathVariable String id) { return service.mapContext(id); }
    @GetMapping("/executions/{id}/ticks/{index}/map") public Object mapSnapshot(@PathVariable String id,@PathVariable int index) { return service.mapSnapshot(id,index); }
    @GetMapping("/executions/{id}/evaluations") public Object evaluations(@PathVariable String id, @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="50") int limit,
            @RequestParam(required=false) Integer fromLoopIndex,@RequestParam(required=false) Integer toLoopIndex) {
        return fromLoopIndex==null && toLoopIndex==null ? service.evaluations(id,offset,limit) : service.evaluations(id,offset,limit,fromLoopIndex,toLoopIndex);
    }
    @GetMapping("/executions/{id}/export") public void export(@PathVariable String id,@RequestParam String format,jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        try(var prepared=service.exportResult(id,format)) {
            response.setContentType("json".equals(format)?"application/json;charset=UTF-8":"text/csv;charset=UTF-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("sandbox-result-"+id+"."+format,java.nio.charset.StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(prepared.size()); prepared.transferTo(response.getOutputStream());
        }
    }
    @GetMapping("/executions/{id}/ticks") public Object ticks(@PathVariable String id, @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="50") int limit) { return service.ticks(id, offset, limit); }
    @GetMapping("/executions/{id}/ticks/{index}") public Object tick(@PathVariable String id, @PathVariable int index) { return service.tick(id, index); }
    @GetMapping("/executions/{id}/verify") public Object verify(@PathVariable String id) { return service.verifyResult(id); }
}
