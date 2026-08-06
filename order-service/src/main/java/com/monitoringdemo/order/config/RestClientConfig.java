package com.monitoringdemo.order.config;

import com.monitoringdemo.common.web.CorrelationIdClientInterceptor;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gắn {@link CorrelationIdClientInterceptor} (bean có sẵn từ module common) vào
 * RestClient.Builder mặc định của Spring Boot, để mọi lệnh gọi sang user-service/product-service
 * đều mang theo correlationId của request gốc.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer correlationIdRestClientCustomizer(CorrelationIdClientInterceptor interceptor) {
        return builder -> builder.requestInterceptor(interceptor);
    }
}
