package com.monitor.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitor.server.model.entity.AlertEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {
}

