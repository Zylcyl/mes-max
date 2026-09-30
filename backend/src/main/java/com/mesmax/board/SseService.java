package com.mesmax.board;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SSE 实时推送。
 *
 * <p>选 SSE 而不是 WebSocket 的原因：车间看板只需要「服务端 → 大屏」单向推送，
 * SSE 基于 HTTP 原生支持断线重连，不需要额外协议与心跳维护，浏览器 EventSource 一行代码即可接入。
 */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    /**
     * 最大并发订阅数。大屏 + 若干办公电脑的正常用量远低于此值；
     * 设上限是为了防止连接被反复打开（或压测脚本）把采集线程拖住 ——
     * 超出后淘汰最早的订阅，保证新的大屏能接进来。
     */
    private static final int MAX_SUBSCRIBERS = 64;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 推送线程池：单线程 + 有界队列 + 丢弃最旧任务。
     *
     * <p>为什么必须异步：{@code publish} 原本是在 {@code MachineSimulator.tick()} 里同步调用的，
     * 只要有一个客户端读得慢（挂墙大屏的浏览器卡住、网络抖动），
     * {@code emitter.send} 就会阻塞，进而把整个 2 秒采集循环一起拖住 ——
     * 一台慢客户端影响所有设备的实时数据。队列满时丢弃最旧的一帧：
     * 看板少一帧遥测无感，采集循环卡住是事故。
     */
    private final ThreadPoolExecutor publisher = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8),
            r -> {
                Thread t = new Thread(r, "mesmax-sse-publisher");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    public SseEmitter subscribe() {
        while (emitters.size() >= MAX_SUBSCRIBERS) {
            SseEmitter oldest = emitters.get(0);
            emitters.remove(oldest);
            try {
                oldest.complete();
            } catch (Exception ignored) {
                // 淘汰失败不影响新订阅
            }
            log.warn("SSE 订阅数已达上限 {}，已淘汰最早的订阅", MAX_SUBSCRIBERS);
        }
        // 0 表示不过期（application.yml 中 async request-timeout=0）
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("msg", "real-time channel ready")));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        log.info("SSE 订阅接入，当前订阅数：{}", emitters.size());
        return emitter;
    }

    public void publish(String event, Object data) {
        if (emitters.isEmpty()) return;
        try {
            publisher.execute(() -> dispatch(event, data));
        } catch (Exception e) {
            // 线程池已关闭或队列拒绝：直接丢弃这一帧，绝不影响调用方（采集循环）
        }
    }

    private void dispatch(String event, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public int subscriberCount() {
        return emitters.size();
    }

    @PreDestroy
    public void shutdown() {
        publisher.shutdownNow();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 关闭阶段的异常无需处理
            }
        }
        emitters.clear();
    }
}
