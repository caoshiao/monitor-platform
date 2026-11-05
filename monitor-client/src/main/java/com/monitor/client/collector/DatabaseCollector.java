package com.monitor.client.collector;

import com.monitor.client.config.ClientConfig;
import com.monitor.common.model.HighgoMetrics;

/**
 * 数据库指标采集器扩展点。
 *
 * <p>调度器只依赖本接口。不同数据库产品通过配置选择采集器标识，后续新增数据库方言时
 * 仅需新增实现类，无需修改调度、WebSocket 消息或前端页面。</p>
 */
public interface DatabaseCollector {

    /**
     * 返回采集器在配置文件中的唯一标识。
     *
     * @return 小写采集器标识
     */
    String getCollectorType();

    /**
     * 采集一个数据库实例的指标。
     *
     * @param clientId 客户端唯一标识
     * @param clientConfig 客户端采集配置
     * @return 数据库实时指标
     */
    HighgoMetrics collect(String clientId, ClientConfig clientConfig);
}
