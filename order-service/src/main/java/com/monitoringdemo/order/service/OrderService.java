package com.monitoringdemo.order.service;

import com.monitoringdemo.common.exception.ResourceNotFoundException;
import com.monitoringdemo.order.client.ProductClient;
import com.monitoringdemo.order.client.UserClient;
import com.monitoringdemo.order.client.dto.ProductResponse;
import com.monitoringdemo.order.domain.Order;
import com.monitoringdemo.order.domain.OrderStatus;
import com.monitoringdemo.order.dto.OrderRequest;
import com.monitoringdemo.order.dto.OrderResponse;
import com.monitoringdemo.order.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final MeterRegistry meterRegistry;

    public OrderResponse createOrder(OrderRequest request) {
        log.info("Bắt đầu tạo order cho userId={} productId={} quantity={}",
                request.userId(), request.productId(), request.quantity());

        try {
            log.info("Gọi user-service để xác nhận userId={}", request.userId());
            userClient.getUser(request.userId());

            log.info("Gọi product-service để lấy thông tin productId={}", request.productId());
            ProductResponse product = productClient.getProduct(request.productId());

            log.info("Gọi product-service để trừ tồn kho productId={} quantity={}",
                    request.productId(), request.quantity());
            productClient.reserveStock(request.productId(), request.quantity());

            BigDecimal totalPrice = product.price().multiply(BigDecimal.valueOf(request.quantity()));
            Order saved = orderRepository.save(new Order(
                    request.userId(), request.productId(), request.quantity(), totalPrice, OrderStatus.CREATED));

            log.info("Đã tạo order id={} totalPrice={}", saved.getId(), saved.getTotalPrice());
            recordOrderCreated(OrderStatus.CREATED, totalPrice);
            return OrderResponse.from(saved);
        } catch (RuntimeException ex) {
            recordOrderCreated(OrderStatus.FAILED, null);
            throw ex;
        }
    }

    /**
     * Tag chỉ theo {@code status} (cardinality thấp, cố định 2 giá trị) — không tag theo
     * orderId/userId để tránh nổ cardinality trên Prometheus. Xem mục "Ranh giới business
     * metrics" trong observability/README.md.
     */
    private void recordOrderCreated(OrderStatus status, BigDecimal totalPrice) {
        Counter.builder("orders.created")
                .tag("status", status.name())
                .register(meterRegistry)
                .increment();
        if (totalPrice != null) {
            DistributionSummary.builder("orders.value")
                    .baseUnit("VND")
                    .register(meterRegistry)
                    .record(totalPrice.doubleValue());
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        Order order = orderRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy order id=" + id));
        return OrderResponse.from(order);
    }
}
