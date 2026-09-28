package org.example.roadsimulation.service;

import org.example.roadsimulation.config.SimulationRuntimeConfig;
import org.example.roadsimulation.core.SimulationTick;
import org.example.roadsimulation.dto.CreateProductionPlanRequest;
import org.example.roadsimulation.dto.ProductionPlanResponse;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProductionPlan;
import org.example.roadsimulation.repository.ProcessingChainRepository;
import org.example.roadsimulation.repository.ProductionPlanRepository;
import org.example.roadsimulation.sandbox.random.SandboxRandomDomain;
import org.example.roadsimulation.sandbox.random.SplitMix64V1;
import org.example.roadsimulation.sandbox.run.SandboxRunRuntimeContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionDemandGeneratorTest {

    @Test
    void createsAndReleasesOnePlanForEveryActiveChainWithReproducibleUnitQuantity() {
        Fixture fixture = fixture();
        ProcessingChain second = chain(2L, "SECOND");
        ProcessingChain first = chain(1L, "FIRST");
        when(fixture.chainRepository.findByStatus(ProcessingChain.ChainStatus.ACTIVE))
                .thenReturn(List.of(second, first));
        when(fixture.planRepository.findBySimulationRunIdAndGenerationRoundAndChainId(
                any(), any(), any())).thenReturn(Optional.empty());
        when(fixture.catalogService.resolveFinalTransportedGoods(1L)).thenReturn(goods("PANEL", 0.1));
        when(fixture.catalogService.resolveFinalTransportedGoods(2L)).thenReturn(goods("TIRE", 0.09));
        ProductionPlanResponse firstResponse = mock(ProductionPlanResponse.class);
        ProductionPlanResponse secondResponse = mock(ProductionPlanResponse.class);
        when(firstResponse.id()).thenReturn(101L);
        when(secondResponse.id()).thenReturn(102L);
        when(fixture.planningService.createAutomaticPlan(any(), eq("run-1"), eq(6)))
                .thenReturn(firstResponse, secondResponse);

        fixture.generator.generate("run-1", tick(6));

        ArgumentCaptor<CreateProductionPlanRequest> requests =
                ArgumentCaptor.forClass(CreateProductionPlanRequest.class);
        verify(fixture.planningService, times(2))
                .createAutomaticPlan(requests.capture(), eq("run-1"), eq(6));
        assertThat(requests.getAllValues()).extracting(CreateProductionPlanRequest::chainId)
                .containsExactly(1L, 2L);
        assertUnitQuantity(requests.getAllValues().get(0), 0.1);
        assertUnitQuantity(requests.getAllValues().get(1), 0.09);
        verify(fixture.planningService).releasePlan(101L, "production-demand-generator");
        verify(fixture.planningService).releasePlan(102L, "production-demand-generator");
    }

    @Test
    void repeatedRunRoundChainDoesNotCreateOrReleaseAnAlreadyReleasedPlan() {
        Fixture fixture = fixture();
        ProcessingChain chain = chain(1L, "FIRST");
        ProductionPlan existing = new ProductionPlan();
        existing.setId(100L);
        existing.setStatus(ProductionPlan.PlanStatus.RELEASED);
        when(fixture.chainRepository.findByStatus(ProcessingChain.ChainStatus.ACTIVE))
                .thenReturn(List.of(chain));
        when(fixture.planRepository.findBySimulationRunIdAndGenerationRoundAndChainId(
                "run-1", 6, 1L)).thenReturn(Optional.of(existing));

        fixture.generator.generate("run-1", tick(6));
        fixture.generator.generate("run-1", tick(6));

        verify(fixture.catalogService, never()).resolveFinalTransportedGoods(any());
        verify(fixture.planningService, never()).createAutomaticPlan(any(), any(), any(Integer.class));
        verify(fixture.planningService, never()).releasePlan(any(), any());
    }

    @Test
    void retryReleasesAnExistingCalculatedPlanWithoutCreatingAnotherPlan() {
        Fixture fixture = fixture();
        ProcessingChain chain = chain(1L, "FIRST");
        ProductionPlan existing = new ProductionPlan();
        existing.setId(100L);
        existing.setStatus(ProductionPlan.PlanStatus.CALCULATED);
        when(fixture.chainRepository.findByStatus(ProcessingChain.ChainStatus.ACTIVE))
                .thenReturn(List.of(chain));
        when(fixture.planRepository.findBySimulationRunIdAndGenerationRoundAndChainId(
                "run-1", 6, 1L)).thenReturn(Optional.of(existing));

        fixture.generator.generate("run-1", tick(6));

        verify(fixture.planningService, never()).createAutomaticPlan(any(), any(), any(Integer.class));
        verify(fixture.planningService).releasePlan(100L, "production-demand-generator");
    }

    @Test
    void sandboxQuantityUsesFinalQuantityDomainAndStableChainKey() {
        Fixture fixture = fixture();
        ProcessingChain chain = chain(7L, "CHAIN-7");
        SandboxRunRuntimeContext runtime = mock(SandboxRunRuntimeContext.class);
        Map<String, Object> key = Map.of("loopIndex", 12, "processingChainId", 7L);
        when(runtime.deriveSeed(SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY, key))
                .thenReturn(12345L);
        when(runtime.random(SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY, key))
                .thenReturn(new SplitMix64V1(12345L));
        fixture.generator.setSandboxRunRuntimeContext(runtime);
        when(fixture.chainRepository.findByStatus(ProcessingChain.ChainStatus.ACTIVE))
                .thenReturn(List.of(chain));
        when(fixture.planRepository.findBySimulationRunIdAndGenerationRoundAndChainId(
                "sandbox-run", 12, 7L)).thenReturn(Optional.empty());
        when(fixture.catalogService.resolveFinalTransportedGoods(7L)).thenReturn(goods("FINAL", 1.0));
        ProductionPlanResponse response = mock(ProductionPlanResponse.class);
        when(response.id()).thenReturn(99L);
        when(fixture.planningService.createAutomaticPlan(any(), eq("sandbox-run"), eq(12)))
                .thenReturn(response);

        fixture.generator.generate("sandbox-run", tick(12));

        ArgumentCaptor<CreateProductionPlanRequest> request =
                ArgumentCaptor.forClass(CreateProductionPlanRequest.class);
        verify(fixture.planningService).createAutomaticPlan(
                request.capture(), eq("sandbox-run"), eq(12));
        assertThat(request.getValue().randomSeed()).isEqualTo(12345L);
        verify(runtime).random(SandboxRandomDomain.PRODUCTION_FINAL_QUANTITY, key);
    }

    private Fixture fixture() {
        ProcessingChainRepository chainRepository = mock(ProcessingChainRepository.class);
        ProductionPlanRepository planRepository = mock(ProductionPlanRepository.class);
        ProductionPlanningService planningService = mock(ProductionPlanningService.class);
        ProductionDemandCatalogService catalogService = mock(ProductionDemandCatalogService.class);
        SimulationRuntimeConfig runtimeConfig = new SimulationRuntimeConfig();
        runtimeConfig.setDemandRandomSeed(20260922L);
        ProductionDemandGenerator generator = new ProductionDemandGenerator(
                chainRepository, planRepository, planningService, catalogService, runtimeConfig);
        return new Fixture(
                generator, chainRepository, planRepository, planningService, catalogService);
    }

    private void assertUnitQuantity(CreateProductionPlanRequest request, double unitWeight) {
        assertThat(request.minFinalWeight()).isEqualTo(request.maxFinalWeight());
        double quantity = request.minFinalWeight() / unitWeight;
        assertThat(quantity).isBetween(10.0, 34.0);
        assertThat(quantity).isCloseTo(Math.rint(quantity),
                org.assertj.core.data.Offset.offset(0.000001));
    }

    private ProcessingChain chain(Long id, String code) {
        ProcessingChain chain = new ProcessingChain();
        chain.setId(id);
        chain.setChainCode(code);
        chain.setStatus(ProcessingChain.ChainStatus.ACTIVE);
        return chain;
    }

    private Goods goods(String sku, double unitWeight) {
        Goods goods = new Goods(sku, sku);
        goods.setWeightPerUnit(unitWeight);
        return goods;
    }

    private SimulationTick tick(int round) {
        return SimulationTick.of(
                round,
                LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(30L * round),
                Duration.ofMinutes(30)
        );
    }

    private record Fixture(
            ProductionDemandGenerator generator,
            ProcessingChainRepository chainRepository,
            ProductionPlanRepository planRepository,
            ProductionPlanningService planningService,
            ProductionDemandCatalogService catalogService
    ) {}
}
