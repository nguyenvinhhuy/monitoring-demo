package com.monitoringdemo.common.config;

import com.monitoringdemo.common.exception.GlobalExceptionHandler;
import com.monitoringdemo.common.web.CorrelationIdClientInterceptor;
import com.monitoringdemo.common.web.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Tự động đăng ký các bean dùng chung (exception handler, correlation-id filter/interceptor)
 * cho bất kỳ service nào add dependency {@code common} — không cần mỗi service tự khai báo lại.
 */
@AutoConfiguration
public class CommonWebAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Integer.MIN_VALUE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public CorrelationIdClientInterceptor correlationIdClientInterceptor() {
        return new CorrelationIdClientInterceptor();
    }
}
