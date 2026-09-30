package com.mesmax.board;

import com.mesmax.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** 车间实时看板接口。 */
@RestController
@RequestMapping("/board")
public class BoardController {

    private final BoardService boardService;
    private final SseService sseService;

    public BoardController(BoardService boardService, SseService sseService) {
        this.boardService = boardService;
        this.sseService = sseService;
    }

    /** 看板总览：KPI + 机台矩阵 + 在制工单 + 安灯 + 报警 + 节拍。 */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(boardService.overview());
    }

    /** 实时数据流（SSE）。大屏订阅后每 2 秒收到一次机台实时态。 */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream() {
        return sseService.subscribe();
    }

    @GetMapping("/matrix")
    public R<List<LiveState>> matrix() {
        return R.ok(boardService.machineMatrix());
    }

    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "14") int days) {
        return R.ok(boardService.trend(days));
    }

    @GetMapping("/ranking")
    public R<Map<String, Object>> ranking(@RequestParam(defaultValue = "7") int days) {
        return R.ok(Map.of(
                "stop", boardService.stopRanking(days),
                "defect", boardService.defectRanking(days)));
    }

    /** 大屏健康检查：订阅数，便于部署时确认推送是否正常。 */
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        return R.ok(Map.of("subscribers", sseService.subscriberCount(), "ok", true));
    }
}
