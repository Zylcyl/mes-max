package com.mesmax.board;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** 一台机床的实时快照（内存态，供看板秒级刷新使用，避免每次请求都打数据库）。 */
@Data
public class LiveState {
    private Long machineId;
    private String machineCode;
    private String machineName;
    private String cncType;
    private Long workCenterId;
    private String workCenterName;

    /** 运行 / 待机 / 停机 / 报警 / 离线 / 保养中 */
    private String status;
    private LocalDateTime statusSince;
    private String statusReason;

    private String orderNo;
    private String productName;
    private String procName;
    private String programNo;
    private String toolNo;
    private String operator;
    private String alarmCode;

    private BigDecimal spindleSpeed;
    private BigDecimal feedRate;
    private BigDecimal feedOverride;
    private BigDecimal spindleLoad;
    private BigDecimal axisX;
    private BigDecimal axisY;
    private BigDecimal axisZ;
    private BigDecimal powerKw;
    private BigDecimal coolantOn;

    private Integer partCount;
    private Integer planQty;
    private Integer finishedQty;
    private LocalDateTime ts;

    /** 最近 90 个采样点，用于实时曲线（内存环形队列）。 */
    private final Deque<Map<String, Object>> history = new ArrayDeque<>(120);
    private List<Map<String, Object>> alarmHistory;

    public synchronized void push(Map<String, Object> point) {
        history.addLast(point);
        while (history.size() > 90) history.removeFirst();
    }

    public synchronized List<Map<String, Object>> snapshotHistory() {
        return List.copyOf(history);
    }
}
