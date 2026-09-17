package org.example.roadsimulation.controller;
import org.example.roadsimulation.dto.*;
import org.example.roadsimulation.service.WeatherEnvironmentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController
@RequestMapping("/api/simulation/weather")
public class WeatherController {
    private final WeatherEnvironmentService weather;
    public WeatherController(WeatherEnvironmentService weather){this.weather=weather;}
    @GetMapping("/scenarios") public List<WeatherScenarioDTO> list(){return weather.list();}
    @PostMapping("/scenarios") public WeatherScenarioDTO create(@RequestBody WeatherScenarioDTO dto){return weather.create(dto,false);}
    @PostMapping("/scenarios/import") public WeatherScenarioDTO importScene(@RequestBody WeatherScenarioDTO dto){return weather.create(dto,true);}
    @GetMapping("/scenarios/{id}") public WeatherScenarioDTO exportScene(@PathVariable Long id){return weather.load(id);}
    @GetMapping("/current") public WeatherCurrentDTO current(){return weather.current();}
    @GetMapping("/runs/{id}") public Map<String,Object> run(@PathVariable String id){return weather.exportRun(id);}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,String>> invalidJson() {
        return ResponseEntity.badRequest().body(Map.of("message", "场景 JSON 格式或字段类型不正确；分钟数必须为整数"));
    }
}
