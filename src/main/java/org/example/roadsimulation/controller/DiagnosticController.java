package org.example.roadsimulation.controller;

import org.example.roadsimulation.util.GaodeDiagnosticService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {

    private final GaodeDiagnosticService diagnosticService;

    public DiagnosticController(GaodeDiagnosticService diagnosticService) {
        this.diagnosticService = diagnosticService;
    }

    @GetMapping("/gaode")
    public String diagnoseGaodeApi() {
        diagnosticService.diagnoseApiKey();
        return "诊断完成，请查看控制台输出";
    }
}