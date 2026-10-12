package org.example.roadsimulation.sandbox.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.*;

@Configuration(proxyBeanMethods = false)
@Profile("!sandbox-runtime & !sandbox-controlled-runtime")
@ConditionalOnProperty(name = "sandbox.management.enabled", havingValue = "true")
public class SandboxManagementConfiguration implements WebMvcConfigurer {
    @Bean SandboxManagementSettings sandboxManagementSettings(Environment environment) {
        return new SandboxManagementSettings(environment.getProperty("sandbox.management.jdbc-url"),
                environment.getProperty("sandbox.management.username", "road_sandbox_runtime"),
                environment.getProperty("sandbox.management.password"));
    }
    @Bean SandboxManagementService sandboxManagementService(SandboxManagementSettings settings, ObjectMapper json) { return new SandboxManagementService(settings, json); }
    @Bean(destroyMethod = "close") SandboxJobCoordinator sandboxJobCoordinator(SandboxManagementSettings settings) { return new SandboxJobCoordinator(settings, new SandboxWorkerLauncher()); }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(new SandboxLocalRequestGuard()).addPathPatterns("/api/sandbox/**"); }
}
