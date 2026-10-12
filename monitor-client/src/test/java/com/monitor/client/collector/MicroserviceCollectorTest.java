package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
import com.monitor.common.model.MicroserviceMetrics;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 外部系统 TCP 与 HTTP 双探针采集测试。 */
class MicroserviceCollectorTest {

    private static final String LOOPBACK_ADDRESS = "127.0.0.1";
    private static final String HEALTH_PATH = "/health";
    private static final String HEALTH_BODY = "{\"status\":\"UP\"}";

    @Test
    void shouldReportUpWhenTcpAndInterfaceAreAvailable() throws IOException {
        HttpServer server = createServer(200, HEALTH_BODY);
        try {
            ClientConfig config = createConfig(server.getAddress().getPort(), "status");

            MicroserviceMetrics metrics = new MicroserviceCollector().collect("test-client", config);

            assertEquals(1, metrics.getTotalServices());
            assertEquals(1, metrics.getHealthyServices());
            assertEquals(0, metrics.getDegradedServices());
            assertEquals(0, metrics.getUnhealthyServices());
            assertEquals("UP", metrics.getServices().get(0).getNetworkStatus());
            assertEquals("UP", metrics.getServices().get(0).getInterfaceStatus());
            assertEquals(200, metrics.getServices().get(0).getHttpStatusCode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReportDegradedWhenPortAvailableButInterfaceReturnsError() throws IOException {
        HttpServer server = createServer(503, "maintenance");
        try {
            ClientConfig config = createConfig(server.getAddress().getPort(), null);

            MicroserviceMetrics metrics = new MicroserviceCollector().collect("test-client", config);

            assertEquals(0, metrics.getHealthyServices());
            assertEquals(1, metrics.getDegradedServices());
            assertEquals(1, metrics.getUnhealthyServices());
            assertEquals("DEGRADED", metrics.getServices().get(0).getHealthStatus());
            assertEquals("UP", metrics.getServices().get(0).getNetworkStatus());
            assertEquals("DOWN", metrics.getServices().get(0).getInterfaceStatus());
            assertEquals(503, metrics.getServices().get(0).getHttpStatusCode());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer createServer(int statusCode, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK_ADDRESS, 0), 0);
        server.createContext(HEALTH_PATH, exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private ClientConfig createConfig(int port, String expectedBody) {
        ClientConfig.ServiceEndpoint endpoint = new ClientConfig.ServiceEndpoint();
        endpoint.setName("测试外部系统");
        endpoint.setHost(LOOPBACK_ADDRESS);
        endpoint.setPort(port);
        endpoint.setHealthUrl("http://" + LOOPBACK_ADDRESS + ":" + port + HEALTH_PATH);
        endpoint.setExpectedStatusMax(399);
        endpoint.setExpectedBodyContains(expectedBody);

        ClientConfig config = new ClientConfig();
        config.setMicroservices(Collections.singletonList(endpoint));
        config.setMicroserviceProbeParallelism(2);
        return config;
    }
}
