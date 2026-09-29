package org.example.roadsimulation;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.controller.TransportRandomEventController;
import org.example.roadsimulation.dto.RandomEventTriggerRequest;
import org.example.roadsimulation.entity.TransportRandomEvent;
import org.example.roadsimulation.service.TransportRandomEventService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationMutationLockTest {
    @Test void resetRejectsManualEventBeforeAnyDatabaseWrite() {
        var clock=new SimulationContext();clock.beginReset();
        var loop=new SimulationMainLoop(null,null,clock,null,null,null);
        var events=mock(TransportRandomEventService.class);
        var controller=new TransportRandomEventController(events,clock);controller.setMainLoop(loop);
        var request=new RandomEventTriggerRequest();request.setVehicleId(1L);
        request.setEventType(TransportRandomEvent.EventType.TRAFFIC_CONGESTION);
        assertEquals(HttpStatus.CONFLICT,controller.trigger(request).getStatusCode());
        verifyNoInteractions(events);
    }

    @Test void concurrentMutationsCannotEnterTheSameTickWindow() throws Exception {
        var loop=new SimulationMainLoop(null,null,new SimulationContext(),null,null,null);
        var entered=new CountDownLatch(1);var attempted=new CountDownLatch(1);var release=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->loop.withSimulationMutationLock(()->{
                entered.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test timeout"); }
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
                return 1;
            }));
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            var second=executor.submit(()->{attempted.countDown();return loop.withSimulationMutationLock(()->2);});
            assertTrue(attempted.await(10,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->second.get(100,TimeUnit.MILLISECONDS));
            release.countDown();assertEquals(1,first.get(10,TimeUnit.SECONDS));assertEquals(2,second.get(10,TimeUnit.SECONDS));
        } finally {release.countDown();executor.shutdownNow();}
    }
}
