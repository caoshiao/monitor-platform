package com.monitor.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** HighGo/PostgreSQL compatible database monitoring metrics. */
@Data
public class HighgoMetrics {

    private String clientId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectTime;

    private long connectionCount;
    private long activeConnectionCount;
    private double load;
    private long loginFailureCount;
    private long repeatedLargeTableQueryCount;
    private List<LargeTableQuery> repeatedLargeTableQueries = new ArrayList<>();

    @Data
    public static class LargeTableQuery {
        private String userName;
        private String clientAddress;
        private String query;
        private long occurrences;
    }
}
