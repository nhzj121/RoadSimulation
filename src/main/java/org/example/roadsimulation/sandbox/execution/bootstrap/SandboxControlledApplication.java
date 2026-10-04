package org.example.roadsimulation.sandbox.execution.bootstrap;

import org.example.roadsimulation.RoadSimulationApplication;
import org.example.roadsimulation.config.TimeModuleConfig;
import org.example.roadsimulation.sandbox.workspace.SandboxRuntimePhaseOneBlocker;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deliberately separate bootstrap: no web, import runners or scheduling infrastructure. */
@Configuration(proxyBeanMethods = false)
@Profile("sandbox-controlled-runtime")
@EnableAutoConfiguration
@EnableCaching
@EntityScan("org.example.roadsimulation")
@EnableJpaRepositories("org.example.roadsimulation.repository")
@ComponentScan(basePackages = "org.example.roadsimulation", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                RoadSimulationApplication.class, TimeModuleConfig.class, SandboxRuntimePhaseOneBlocker.class,
                org.example.roadsimulation.service.impl.VehicleDataImportServiceImpl.class}),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = {Controller.class, SpringBootConfiguration.class}),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = SandboxTestComponentFilter.class)
})
public class SandboxControlledApplication {
    @Bean static BeanFactoryPostProcessor forbidScheduling() {
        return factory -> {
            if (factory.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class, false, false).length != 0) {
                throw new IllegalStateException("Controlled sandbox must not contain a scheduled annotation processor");
            }
            if (factory.getBeanNamesForType(org.example.roadsimulation.service.VehicleDataImportService.class, false, false).length != 0) {
                throw new IllegalStateException("Controlled sandbox must not contain the startup vehicle importer");
            }
        };
    }

    public static ConfigurableApplicationContext open(String jdbcUrl, String username, String password) {
        return open(jdbcUrl, username, password, Map.of());
    }

    /** Extra properties are for isolated integration fixtures, never CLI password arguments. */
    public static ConfigurableApplicationContext open(String jdbcUrl, String username, String password,
                                                       Map<String, Object> additionalProperties,
                                                       Class<?>... additionalSources) {
        if (!org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceSafety.DATABASE_NAME.equals(
                new org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceSafety().databaseName(jdbcUrl))) {
            throw new org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException("UNSAFE_DATABASE",
                    "Controlled bootstrap requires the dedicated sandbox database");
        }
        Map<String, Object> configuration = new LinkedHashMap<>(additionalProperties);
        configuration.put("spring.datasource.url", jdbcUrl);
        configuration.put("spring.datasource.username", username);
        configuration.put("spring.datasource.password", password);
        configuration.put("spring.datasource.hikari.connection-init-sql",
                "SET SESSION sql_mode='NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION'");
        configuration.put("spring.jpa.hibernate.ddl-auto", "validate");
        // XAMPP and the standard integration image both run MariaDB, not MySQL.
        // Do not inherit the ordinary application's explicitly forced MySQL dialect.
        configuration.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        configuration.put("spring.jpa.open-in-view", false);
        configuration.put("spring.jpa.show-sql", false);
        configuration.put("spring.sql.init.mode", "never");
        configuration.put("spring.devtools.restart.enabled", false);
        configuration.put("app.vehicle.import.enabled", false);
        configuration.put("app.vehicle.import.auto-startup", false);
        configuration.put("app.poi.import.enabled", false);
        configuration.put("app.goods.import.enabled", false);
        configuration.put("app.simulation.startup-pre-generation.enabled", false);
        return new SpringApplicationBuilder(SandboxControlledApplication.class)
                .sources(additionalSources)
                .web(WebApplicationType.NONE)
                .profiles("sandbox-runtime", "sandbox-controlled-runtime")
                .registerShutdownHook(false)
                .initializers(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("sandbox-controlled-safety", configuration)))
                .run();
    }
}
