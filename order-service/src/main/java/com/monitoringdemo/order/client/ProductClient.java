package com.monitoringdemo.order.client;

import com.monitoringdemo.common.exception.ResourceNotFoundException;
import com.monitoringdemo.common.exception.UpstreamServiceException;
import com.monitoringdemo.order.client.dto.ProductResponse;
import com.monitoringdemo.order.client.dto.ReserveStockRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gọi sang product-service để lấy giá + trừ tồn kho khi tạo order. */
@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(RestClient.Builder restClientBuilder, @Value("${services.product.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public ProductResponse getProduct(Long productId) {
        try {
            return restClient.get().uri("/api/products/{id}", productId).retrieve().body(ProductResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Không tìm thấy product id=" + productId);
        } catch (RestClientException ex) {
            throw new UpstreamServiceException("Gọi product-service thất bại cho productId=" + productId, ex);
        }
    }

    public ProductResponse reserveStock(Long productId, int quantity) {
        try {
            return restClient
                    .post()
                    .uri("/api/products/{id}/reserve", productId)
                    .body(new ReserveStockRequest(quantity))
                    .retrieve()
                    .body(ProductResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Không tìm thấy product id=" + productId);
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new IllegalArgumentException("Không đủ tồn kho cho product id=" + productId);
        } catch (RestClientException ex) {
            throw new UpstreamServiceException("Gọi product-service (reserve) thất bại cho productId=" + productId, ex);
        }
    }
}
