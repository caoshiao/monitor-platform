package com.monitor.server.scheduler;

import com.monitor.server.websocket.FrontendWsHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 推送调度器 —— 定时将聚合后的指标数据广播给所有已连接的前端页面。
 * <p>
 * 每 3 秒触发一次，调用 {@link FrontendWsHandler#pushToFrontend()}。
 * 推送频率可通过修改 {@code fixedDelay} 调整。
 * </p>
 *
 * @author csa
 * @see FrontendWsHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushScheduler {

    /** 前端 WebSocket 推送处理器 */
    private final FrontendWsHandler frontendWsHandler;

    /**
     * 每 3 秒向前端页面推送一次聚合指标快照。
     * <p>当无前端连接时，FrontendWsHandler 内部跳过序列化，无性能损耗。</p>
     */
    @Scheduled(fixedDelay = 3000)
    public void pushToFrontend() {
        frontendWsHandler.pushToFrontend();
    }
}
