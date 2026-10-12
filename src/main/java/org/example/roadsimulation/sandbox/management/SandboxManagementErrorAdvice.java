package org.example.roadsimulation.sandbox.management;

import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.example.roadsimulation.sandbox.run.SandboxRunException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Never return raw SQL/connection exceptions or credentials to the client. */
@RestControllerAdvice(assignableTypes = SandboxManagementController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SandboxManagementErrorAdvice {
    @ExceptionHandler(Exception.class) public ResponseEntity<?> failure(Exception failure) {
        String code = failure instanceof SandboxWorkspaceException workspace ? workspace.errorCode()
                : failure instanceof SandboxRunException run ? run.errorCode()
                : failure instanceof org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioException scenario ? scenario.errorCode()
                : failure instanceof org.springframework.web.bind.MissingServletRequestParameterException
                  || failure instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ? "INVALID_ARGUMENT" : "SANDBOX_REQUEST_FAILED";
        HttpStatus status = code.equals("LOCAL_ACCESS_REQUIRED") ? HttpStatus.FORBIDDEN
                : code.equals("DRAFT_CONFLICT") || code.equals("START_REQUEST_CONFLICT") || code.equals("WORKSPACE_BUSY") || code.equals("EXECUTION_STILL_RUNNING") || code.equals("CANCEL_NOT_ALLOWED") || code.equals("INSPECTION_REQUIRED") || code.equals("VERIFICATION_BUSY") ? HttpStatus.CONFLICT
                : code.contains("NOT_FOUND") ? HttpStatus.NOT_FOUND
                : code.equals("EXECUTION_RECORD_CORRUPT") ? HttpStatus.INTERNAL_SERVER_ERROR
                : code.contains("DATABASE") || code.contains("CONTROL_SCHEMA") || code.contains("READ_FAILED") ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_REQUEST;
        var body=new java.util.LinkedHashMap<String,Object>(); body.put("errorCode",code); body.put("message","Sandbox request failed; inspect the error code and backend configuration");
        if(failure instanceof SandboxResultInspectionException inspection) { body.put("jobId",inspection.jobId()); body.put("reason",inspection.reason()); }
        return ResponseEntity.status(status).body(body);
    }
}
