package org.example.roadsimulation.service;

import org.example.roadsimulation.repository.POIRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class POIManagerService {

    @Value("${gaode.api.key}")
    private String apiKey;

    private final POIRepository poiRepository;

    @Autowired
    public POIManagerService(POIRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

}
