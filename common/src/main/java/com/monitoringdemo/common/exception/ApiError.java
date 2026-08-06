package com.monitoringdemo.common.exception;

import java.time.Instant;
import java.util.List;

/** Response body chuẩn cho mọi lỗi trả về từ 3 service — giữ nguyên field name để dễ query trên Kibana. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<String> details) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
