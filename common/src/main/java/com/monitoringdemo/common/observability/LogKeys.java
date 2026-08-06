package com.monitoringdemo.common.observability;

/**
 * Tên field MDC dùng chung giữa các service để log JSON (ECS) nhất quán nhau
 * khi lên Kibana — tránh mỗi service tự đặt tên field khác nhau cho cùng một khái niệm.
 */
public final class LogKeys {

    public static final String CORRELATION_ID = "correlationId";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private LogKeys() {
    }
}
