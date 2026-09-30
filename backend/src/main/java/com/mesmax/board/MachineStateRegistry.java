package com.mesmax.board;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 全车间机床实时状态注册表（内存）。 */
@Component
public class MachineStateRegistry {

    private final Map<Long, LiveState> states = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> liveAlarms = new ArrayList<>();

    public void put(LiveState s) {
        states.put(s.getMachineId(), s);
    }

    public LiveState get(Long machineId) {
        return states.get(machineId);
    }

    public List<LiveState> all() {
        return states.values().stream()
                .sorted(Comparator.comparing(LiveState::getMachineId))
                .toList();
    }

    public synchronized void addAlarm(Map<String, Object> alarm) {
        liveAlarms.add(0, alarm);
        while (liveAlarms.size() > 60) liveAlarms.remove(liveAlarms.size() - 1);
    }

    public synchronized List<Map<String, Object>> alarms() {
        return new ArrayList<>(liveAlarms);
    }

    /** 状态计数，看板顶部的状态汇总用。 */
    public Map<String, Integer> statusCount() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String s : List.of("RUN", "IDLE", "STOP", "ALARM", "MAINT", "OFFLINE")) map.put(s, 0);
        for (LiveState s : all()) map.merge(s.getStatus() == null ? "OFFLINE" : s.getStatus(), 1, Integer::sum);
        return map;
    }
}
