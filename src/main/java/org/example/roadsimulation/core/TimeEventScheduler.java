// TimeEventScheduler.java - 时间事件调度器
package org.example.roadsimulation.core;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 时间事件调度器
 * 负责调度和管理基于模拟时间的事件
 */
public class TimeEventScheduler {
    private final SimulationTime simulationTime;
    private final Map<LocalDateTime, List<TimeEvent>> scheduledEvents;
    private final List<TimeEventListener> listeners;

    public TimeEventScheduler(SimulationTime simulationTime) {
        this.simulationTime = simulationTime;
        this.scheduledEvents = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public interface TimeEvent {
        void execute();
        String getEventId();
        LocalDateTime getScheduledTime();
    }

    public interface TimeEventListener {
        void onEventExecuted(TimeEvent event);
        void onEventScheduled(TimeEvent event);
    }

    public void scheduleEvent(TimeEvent event) {
        scheduledEvents
                .computeIfAbsent(event.getScheduledTime(), k -> new ArrayList<>())
                .add(event);

        // 通知监听器
        listeners.forEach(listener -> listener.onEventScheduled(event));
    }

    public void scheduleEvent(LocalDateTime time, Runnable action, String eventId) {
        scheduleEvent(new SimpleTimeEvent(time, action, eventId));
    }

    public void scheduleRelativeEvent(long delayMinutes, Runnable action, String eventId) {
        LocalDateTime scheduledTime = simulationTime.getCurrentTime().plusMinutes(delayMinutes);
        scheduleEvent(scheduledTime, action, eventId);
    }

    public void processDueEvents() {
        LocalDateTime currentTime = simulationTime.getCurrentTime();

        scheduledEvents.entrySet().removeIf(entry -> {
            if (!entry.getKey().isAfter(currentTime)) {
                // 执行到期事件
                entry.getValue().forEach(event -> {
                    try {
                        event.execute();
                        listeners.forEach(listener -> listener.onEventExecuted(event));
                    } catch (Exception e) {
                        System.err.println("执行时间事件失败: " + event.getEventId() + ", 错误: " + e.getMessage());
                    }
                });
                return true; // 移除已处理的事件
            }
            return false;
        });
    }

    public void addListener(TimeEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TimeEventListener listener) {
        listeners.remove(listener);
    }

    public int getPendingEventCount() {
        return scheduledEvents.values().stream().mapToInt(List::size).sum();
    }

    // 简单时间事件实现
    private static class SimpleTimeEvent implements TimeEvent {
        private final LocalDateTime scheduledTime;
        private final Runnable action;
        private final String eventId;

        public SimpleTimeEvent(LocalDateTime scheduledTime, Runnable action, String eventId) {
            this.scheduledTime = scheduledTime;
            this.action = action;
            this.eventId = eventId;
        }

        @Override
        public void execute() {
            action.run();
        }

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public LocalDateTime getScheduledTime() {
            return scheduledTime;
        }
    }
}