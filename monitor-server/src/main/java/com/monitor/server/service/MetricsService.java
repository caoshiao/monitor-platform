package com.monitor.server.service;

import com.monitor.common.model.DockerMetrics;
import com.monitor.common.model.HighgoMetrics;
import com.monitor.common.model.MicroserviceMetrics;
import com.monitor.common.model.ServerMessage;
import com.monitor.common.model.SystemMetrics;

import java.util.List;

/**
 * 实时指标缓存与聚合业务接口。
 */
public interface MetricsService {

    /**
     * 更新指定客户端的系统指标。
     *
     * @param clientId 客户端标识
     * @param metrics 系统指标
     */
    void updateSystemMetrics(String clientId, SystemMetrics metrics);

    /**
     * 更新指定客户端的 Docker 指标。
     *
     * @param clientId 客户端标识
     * @param metrics Docker 指标
     */
    void updateDockerMetrics(String clientId, DockerMetrics metrics);

    /**
     * 更新指定客户端的微服务指标。
     *
     * @param clientId 客户端标识
     * @param metrics 微服务指标
     */
    void updateMicroserviceMetrics(String clientId, MicroserviceMetrics metrics);

    /**
     * 更新指定客户端的瀚高数据库指标。
     *
     * @param clientId 客户端标识
     * @param metrics 瀚高数据库指标
     */
    void updateHighgoMetrics(String clientId, HighgoMetrics metrics);

    /**
     * 标记客户端在线。
     *
     * @param clientId 客户端标识
     */
    void markOnline(String clientId);

    /**
     * 标记客户端离线。
     *
     * @param clientId 客户端标识
     */
    void markOffline(String clientId);

    /**
     * 查询全部在线客户端标识。
     *
     * @return 在线客户端标识列表
     */
    List<String> getOnlineClients();

    /**
     * 构建全部在线客户端的聚合指标快照。
     *
     * @return 聚合指标消息
     */
    ServerMessage buildAggregatedMessage();
}
