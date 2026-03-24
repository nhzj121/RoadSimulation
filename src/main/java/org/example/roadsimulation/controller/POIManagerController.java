package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.service.POIManagerService;
import org.example.roadsimulation.service.POIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("./poisManager")
public class POIManagerController {

    private final POIManagerService poiManagerService;
    @Autowired
    public POIManagerController(POIManagerService poiManagerService) {
        this.poiManagerService = poiManagerService;
    }

    /*
    依据给出的搜索种类进行规定范围内的POI点搜素
     */
    @GetMapping("/smartSearch")
    public ResponseEntity<POIController.ApiResponse<POI>> smartSearch(){
        return null;
    }

    /*
    保存现有POI点数据到数据库中
     */
    @GetMapping("/saveToData")
    public ResponseEntity<POIController.ApiResponse<POI>> saveToData(){
        return null;
    }

    /*
    从数据库中加载已有的POI点数据
     */
    @PostMapping("/getFromData")
    public ResponseEntity<POIController.ApiResponse<POI>> getFromData(){
        return null;
    }



}
