package org.example.roadsimulation.repository;
import org.example.roadsimulation.entity.WeatherRun;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WeatherRunRepository extends JpaRepository<WeatherRun, String> {}
