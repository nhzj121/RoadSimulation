package org.example.roadsimulation.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/DataInitializer")
public class DataInitializerController {

    private boolean DataInitIsRunning = false;

    @PostMapping("/beginDataInit")
    public void beginDataInit() {

    }

}
