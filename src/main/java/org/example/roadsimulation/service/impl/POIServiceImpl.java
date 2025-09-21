package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.service.POIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class POIServiceImpl implements POIService {

    @Autowired
    private POIRepository poiRepository;

    @Override
    public POI savePOI(POI poi) {
        return poiRepository.save(poi);
    }

    @Override
    public Optional<POI> findById(Long id) {
        return poiRepository.findById(id);
    }

    @Override
    public List<POI> findAll() {
        return poiRepository.findAll();
    }

    @Override
    public List<POI> findByNameContaining(String name) {
        return poiRepository.findByNameContainingIgnoreCase(name);
    }

    @Override
    public void deleteById(Long id) {
        poiRepository.deleteById(id);
    }
}
