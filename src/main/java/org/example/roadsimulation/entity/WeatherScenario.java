package org.example.roadsimulation.entity;

import jakarta.persistence.*;
import lombok.Data;

/** Immutable, self-contained weather input. JSON contains relative simulation offsets. */
@Entity
@Data
public class WeatherScenario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Lob @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String definitionJson;
}
