package com.monitoringdemo.common.web;

import com.monitoringdemo.common.observability.LogKeys;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Gắn vào {@code RestClient} của order-service khi gọi sang user-service/product-service —
 * truyền correlationId hiện tại của request sang header, để log phía nhận vẫn nối được vào
 * cùng correlationId, tạo thành trace log xuyên suốt 3 service trên Kibana.
 */
public class CorrelationIdClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get(LogKeys.CORRELATION_ID);
        if (correlationId != null) {
            request.getHeaders().add(LogKeys.CORRELATION_ID_HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
