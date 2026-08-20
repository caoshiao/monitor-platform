package com.monitor.server.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.monitor.server.mapper")
public class MybatisPlusConfig {
}
