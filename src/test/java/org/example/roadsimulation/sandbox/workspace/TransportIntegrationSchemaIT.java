package org.example.roadsimulation.sandbox.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.example.roadsimulation.service.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** No application startup or source DB access: restores the actual package and validates every entity. */
@Testcontainers
class TransportIntegrationSchemaIT {
    @Container static final MariaDBContainer<?> DB=new MariaDBContainer<>("mariadb:10.4.32")
            .withDatabaseName(SandboxWorkspaceSafety.DATABASE_NAME).withUsername("sandbox").withPassword("sandbox")
            .withCommand("--character-set-server=utf8mb4","--collation-server=utf8mb4_general_ci");
    static SessionFactory factory;
    static StandardServiceRegistry registry;

    @BeforeAll static void restoreAndValidate() throws Exception {
        try(Connection c=DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());Statement s=c.createStatement()) {
            s.execute("""
                CREATE TABLE sandbox_workspace_marker (
                  marker_id TINYINT PRIMARY KEY, workspace_kind VARCHAR(64) NOT NULL,
                  schema_version VARCHAR(64),baseline_id VARCHAR(128),
                  restoration_payload_sha256 CHAR(64),simulation_facts_sha256 CHAR(64),
                  effective_base_data_sha256 CHAR(64),eligibility_policy_version VARCHAR(64),
                  workspace_state ENUM('EMPTY','PREPARING','BASE_DATA_READY','FAILED') NOT NULL,
                  prepared_at DATETIME(6),failure_code VARCHAR(80),failure_message VARCHAR(1000))
                """);
            s.execute("INSERT INTO sandbox_workspace_marker(marker_id,workspace_kind,workspace_state) VALUES(1,'ROAD_SIMULATION_SANDBOX','EMPTY')");
            ScriptUtils.executeSqlScript(c,new ClassPathResource("sandbox/schema/sandbox-control-schema-v2.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource("sandbox/schema/sandbox-control-schema-v3.sql"));
        }
        new SandboxWorkspacePreparer(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword(),new ObjectMapper().findAndRegisterModules())
                .prepare(new ClassPathResource("sandbox/baseline/baseline-v1.json"));
        registry=new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url",DB.getJdbcUrl())
                .applySetting("hibernate.connection.username",DB.getUsername())
                .applySetting("hibernate.connection.password",DB.getPassword())
                .applySetting("hibernate.connection.driver_class",DB.getDriverClassName())
                .applySetting("hibernate.dialect","org.hibernate.dialect.MariaDBDialect")
                .applySetting("hibernate.hbm2ddl.auto","validate")
                .applySetting("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .build();
        var sources=new MetadataSources(registry);
        var scanner=new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for(var bean:scanner.findCandidateComponents("org.example.roadsimulation"))
            sources.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
        factory=sources.buildMetadata().buildSessionFactory();
    }
    @AfterAll static void close() { if(factory!=null)factory.close();if(registry!=null)StandardServiceRegistryBuilder.destroy(registry); }

    @Test void allEntitiesValidateAgainstRestoredSandboxDdlWithoutAutomaticUpdate() {
        assertNotNull(factory);
        try(var em=factory.createEntityManager()) {
            for(String table:new String[]{"weather_scenario","weather_run","transport_random_event","driving_progress",
                    "vehicle_replacement_attempt","transport_execution_segment"})
                assertEquals(0L,((Number)em.createNativeQuery("SELECT COUNT(*) FROM "+table).getSingleResult()).longValue());
        }
    }

    @Test void explicitMigrationIsIdempotentAndPreservesFrozenHistoricalJson() throws Exception {
        try(Connection c=DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());var s=c.createStatement()) {
            try {
                s.execute("INSERT INTO weather_run(id,manually_intervened,frozen_scenario_json) VALUES('migration-history',b'0','{\"legacy\":true}')");
                var script=new ClassPathResource("sandbox/schema/migrations/20260929-transport-weather-integration.sql");
                ScriptUtils.executeSqlScript(c,script);ScriptUtils.executeSqlScript(c,script);
                // Unknown capacity remains representable; energy_valid prevents use in evaluation.
                try(var columns=s.executeQuery("SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='transport_execution_segment' AND COLUMN_NAME='capacity_tonnes'")) {
                    assertTrue(columns.next());assertEquals("YES",columns.getString(1));
                }
                try(var rows=s.executeQuery("SELECT frozen_scenario_json FROM weather_run WHERE id='migration-history'")) {
                    assertTrue(rows.next());assertEquals("{\"legacy\":true}",rows.getString(1));
                }
            } finally {s.execute("DELETE FROM weather_run WHERE id='migration-history'");}
        }
    }

    @Test void concurrentClaimsRefreshTheLockedRowAndCannotReserveTheSameDriverTwice() throws Exception {
        long vehicleId;
        try(var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            var v=new Vehicle();v.setLicensePlate("CT-driver-lock");v.setMaxLoadCapacity(10.0);
            v.setCargoVolume(20.0);v.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
            v.setCreatedTime(LocalDateTime.of(2026,1,1,0,0));em.persist(v);
            var d=new Driver();d.setDriverName("single CT driver");d.setCurrentStatus(Driver.DriverStatus.IDLE);d.addVehicle(v);em.persist(d);
            em.getTransaction().commit();vehicleId=v.getId();
        }
        var claimed=new CountDownLatch(1);var staleRead=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->{
                try(var em=factory.createEntityManager()) {
                    em.getTransaction().begin();
                    var a=new Assignment();a.setId(90001L);
                    boolean result=resources(em).reserveReplacement(em.find(Vehicle.class,vehicleId),a,90001L,LocalDateTime.of(2026,1,1,0,0)).isPresent();
                    claimed.countDown();
                    if(!staleRead.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Second transaction did not read");
                    em.getTransaction().commit();return result;
                }
            });
            var second=executor.submit(()->{
                if(!claimed.await(20,TimeUnit.SECONDS))throw new IllegalStateException("First transaction did not claim");
                try(var em=factory.createEntityManager()) {
                    em.getTransaction().begin();
                    var repository=new JpaRepositoryFactory(em).getRepository(DriverRepository.class);
                    var before=repository.findDriversByVehicleId(vehicleId).get(0);
                    assertNull(before.getReservedReplacementEventId()); // Cached state before first commit.
                    staleRead.countDown();
                    var a=new Assignment();a.setId(90002L);
                    boolean result=resources(em).reserveReplacement(em.find(Vehicle.class,vehicleId),a,90002L,LocalDateTime.of(2026,1,1,0,0)).isPresent();
                    em.getTransaction().commit();return result;
                }
            });
            assertTrue(first.get(30,TimeUnit.SECONDS));assertFalse(second.get(30,TimeUnit.SECONDS));
        } finally { staleRead.countDown();executor.shutdownNow(); }
    }

    private static DriverResourceService resources(EntityManager em) {
        var repositories=new JpaRepositoryFactory(em);
        var service=new DriverResourceService(repositories.getRepository(DriverRepository.class),
                repositories.getRepository(AssignmentRepository.class),repositories.getRepository(AssignmentDriverHistoryRepository.class),
                new DriverPreferenceScorer());
        ReflectionTestUtils.setField(service,"entityManager",em);return service;
    }
}
