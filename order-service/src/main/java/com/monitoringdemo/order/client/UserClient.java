package com.monitoringdemo.order.client;

import com.monitoringdemo.common.exception.ResourceNotFoundException;
import com.monitoringdemo.common.exception.UpstreamServiceException;
import com.monitoringdemo.order.client.dto.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gọi sang user-service để xác nhận user tồn tại trước khi cho phép tạo order. */
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(RestClient.Builder restClientBuilder, @Value("${services.user.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public UserResponse getUser(Long userId) {
        try {
            return restClient.get().uri("/api/users/{id}", userId).retrieve().body(UserResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Không tìm thấy user id=" + userId);
        } catch (RestClientException ex) {
            throw new UpstreamServiceException("Gọi user-service thất bại cho userId=" + userId, ex);
        }
    }
}
