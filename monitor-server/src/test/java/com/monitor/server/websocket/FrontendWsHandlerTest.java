package com.monitor.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monitor.common.model.ServerMessage;
import com.monitor.server.model.dto.AlertEventView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前端 WebSocket 活动告警摘要转换测试。
 */
class FrontendWsHandlerTest {

    @Test
    void shouldConvertActiveAlertToWebSocketSnapshot() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 10, 10, 3, 30, 50);
        AlertEventView alert = new AlertEventView();
        alert.setId(19L);
        alert.setRuleId(3L);
        alert.setRuleName("内存使用率过高");
        alert.setClientId("server-node-01");
        alert.setNodeName("csa");
        alert.setMetric("MEMORY");
        alert.setLevel("WARNING");
        alert.setStartedAt(startedAt);

        List<ServerMessage.AlertSnapshot> snapshots =
                FrontendWsHandler.toAlertSnapshots(List.of(alert));

        assertEquals(1, snapshots.size());
        ServerMessage.AlertSnapshot snapshot = snapshots.get(0);
        assertEquals(19L, snapshot.getId());
        assertEquals("csa", snapshot.getNodeName());
        assertEquals("MEMORY", snapshot.getMetric());
        assertEquals("WARNING", snapshot.getLevel());
        assertEquals(startedAt, snapshot.getStartedAt());
    }

    @Test
    void shouldSerializeAlertTimeAsIsoString() throws Exception {
        ServerMessage.AlertSnapshot snapshot = new ServerMessage.AlertSnapshot();
        snapshot.setStartedAt(LocalDateTime.of(2026, 10, 10, 3, 41, 9, 568423000));
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String json = objectMapper.writeValueAsString(snapshot);

        assertTrue(json.contains("\"startedAt\":\"2026-10-10T03:41:09.568423\""));
    }
}
