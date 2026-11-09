package org.example.roadsimulation.sandbox.cli;

import org.example.roadsimulation.service.GaodeMapService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.atomic.AtomicInteger;

/** Route provider guard for isolated G3 workers, including code that could catch a map exception. */
@Configuration(proxyBeanMethods = false)
@Profile("sandbox-controlled-runtime")
@ConditionalOnProperty(name = "sandbox.g3-offline", havingValue = "true")
public class OfflinePlanMaps {
    private final AtomicInteger requests = new AtomicInteger();

    @Bean @Primary
    GaodeMapService offlinePlanMapService() {
        return request -> {
            requests.incrementAndGet();
            throw new IllegalStateException("G3_MAP_ACCESS_FORBIDDEN");
        };
    }

    void requireUnused() {
        if (requests.get() != 0) throw new IllegalStateException("G3_MAP_ACCESS_FORBIDDEN");
    }
}
