package com.monitor.server.service.impl;

import com.monitor.common.model.DockerMetrics;
import com.monitor.common.model.HighgoMetrics;
import com.monitor.common.model.MicroserviceMetrics;
import com.monitor.common.model.ServerMessage;
import com.monitor.common.model.SystemMetrics;
import com.monitor.server.service.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标数据管理业务实现类，负责存储各客户端的最新指标并生成聚合快照。
 * <p>
 * 使用线程安全的 {@link ConcurrentHashMap} 存储三类指标，支持高并发读写。
 * 同时维护在线客户端集合与主机名映射。
 * </p>
 *
 * <h3>数据流</h3>
 * <ol>
 *   <li>{@link com.monitor.server.websocket.ClientWsHandler} 收到消息后调用对应的 updateXxx 方法写入</li>
 *   <li>{@link com.monitor.server.websocket.FrontendWsHandler} 调用 {@link #buildAggregatedMessage()} 读取</li>
 * </ol>
 *
 * @author csa
 * @see com.monitor.server.websocket.ClientWsHandler
 * @see com.monitor.server.websocket.FrontendWsHandler
 */
@Slf4j
@Service
public class MetricsServiceImpl implements MetricsService {

    /** clientId → 最新系统指标 */
    private final Map<String, SystemMetrics> systemMap = new ConcurrentHashMap<>();

    /** clientId → 最新 Docker 指标 */
    private final Map<String, DockerMetrics> dockerMap = new ConcurrentHashMap<>();

    /** clientId → 最新微服务指标 */
    private final Map<String, MicroserviceMetrics> microserviceMap = new ConcurrentHashMap<>();

    private final Map<String, HighgoMetrics> highgoMap = new ConcurrentHashMap<>();

    /** clientId → 主机名 */
    private final Map<String, String> hostnameMap = new ConcurrentHashMap<>();

    /** clientId → 是否在线 */
    private final Map<String, Boolean> onlineMap = new ConcurrentHashMap<>();

    /**
     * 更新系统指标缓存并记录主机名。
     *
     * @param clientId 客户端标识
     * @param metrics 系统指标
     */
    @Override
    public void updateSystemMetrics(String clientId, SystemMetrics metrics) {
        systemMap.put(clientId, metrics);
        if (metrics.getHostname() != null) {
            hostnameMap.put(clientId, metrics.getHostname());
        }
    }

    /**
     * 更新 Docker 指标缓存。
     *
     * @param clientId 客户端标识
     * @param metrics Docker 指标
     */
    @Override
    public void updateDockerMetrics(String clientId, DockerMetrics metrics) {
        dockerMap.put(clientId, metrics);
    }

    /**
     * 更新微服务指标缓存。
     *
     * @param clientId 客户端标识
     * @param metrics 微服务指标
     */
    /**
     * 更新瀚高数据库指标缓存。
     *
     * @param clientId 客户端标识
     * @param metrics 瀚高数据库指标
     */
    @Override
    public void updateMicroserviceMetrics(String clientId, MicroserviceMetrics metrics) {
        microserviceMap.put(clientId, metrics);
    }

    @Override
    public void updateHighgoMetrics(String clientId, HighgoMetrics metrics) {
        highgoMap.put(clientId, metrics);
    }

    /**
     * 标记客户端上线。
     *
     * @param clientId 客户端标识
     */
    @Override
    public void markOnline(String clientId) {
        onlineMap.put(clientId, true);
        log.info("客户端上线: {}", clientId);
    }

    /**
     * 标记客户端离线。
     *
     * @param clientId 客户端标识
     */
    @Override
    public void markOffline(String clientId) {
        onlineMap.remove(clientId);
        log.info("客户端离线: {}", clientId);
    }

    /**
     * 查询全部在线客户端标识。
     *
     * @return 在线客户端标识列表
     */
    @Override
    public List<String> getOnlineClients() {
        return new ArrayList<>(onlineMap.keySet());
    }

    /**
     * 构建包含全部在线客户端指标的聚合快照。
     *
     * @return 聚合指标消息
     */
    @Override
    public ServerMessage buildAggregatedMessage() {
        ServerMessage msg = new ServerMessage();
        msg.setType("AGGREGATED");
        msg.setTimestamp(LocalDateTime.now());
        msg.setOnlineClients(getOnlineClients());

        List<ServerMessage.ClientSnapshot> snapshots = new ArrayList<>();
        for (String clientId : onlineMap.keySet()) {
            ServerMessage.ClientSnapshot snap = new ServerMessage.ClientSnapshot();
            snap.setClientId(clientId);
            snap.setHostname(hostnameMap.getOrDefault(clientId, "unknown"));
            snap.setStatus("ONLINE");
            snap.setSystemMetrics(systemMap.get(clientId));
            snap.setDockerMetrics(dockerMap.get(clientId));
            snap.setMicroserviceMetrics(microserviceMap.get(clientId));
            snap.setHighgoMetrics(highgoMap.get(clientId));
            SystemMetrics systemMetrics = systemMap.get(clientId);
            if (systemMetrics != null) {
                snap.setHostname(systemMetrics.getHostname());
                snap.setIpAddress(systemMetrics.getIpAddress());
            }
            snapshots.add(snap);
        }
        msg.setClientSnapshots(snapshots);
        return msg;
    }
}
