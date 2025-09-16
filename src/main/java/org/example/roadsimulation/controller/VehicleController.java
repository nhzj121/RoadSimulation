package org.example.roadsimulation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VehicleController {

    @GetMapping("/api/vehicles")
    public String vehicles(){
        return "This is the vehicles!";
    }

}
