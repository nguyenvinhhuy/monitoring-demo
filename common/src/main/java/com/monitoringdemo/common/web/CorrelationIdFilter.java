package com.monitoringdemo.common.web;

import com.monitoringdemo.common.observability.LogKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lấy correlation-id từ header request (nếu request đến từ service khác trong hệ thống)
 * hoặc sinh mới (nếu đây là entrypoint), đưa vào MDC để mọi dòng log ECS của request này
 * đều có chung {@code correlationId} — đây là field dùng để nối log 3 service lại với nhau trên Kibana.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(LogKeys.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(LogKeys.CORRELATION_ID, correlationId);
        response.setHeader(LogKeys.CORRELATION_ID_HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LogKeys.CORRELATION_ID);
        }
    }
}
