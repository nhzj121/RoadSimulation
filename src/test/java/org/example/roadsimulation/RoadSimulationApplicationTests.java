package org.example.roadsimulation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = RoadSimulationApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:road-simulation-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "app.vehicle.import.enabled=false",
                "app.simulation.startup-pre-generation.enabled=false"
        }
)
class RoadSimulationApplicationTests {

	@Test
	void contextLoads() {
	}

}
