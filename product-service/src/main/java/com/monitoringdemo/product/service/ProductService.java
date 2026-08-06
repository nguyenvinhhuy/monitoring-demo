package com.monitoringdemo.product.service;

import com.monitoringdemo.common.exception.ResourceNotFoundException;
import com.monitoringdemo.product.domain.Product;
import com.monitoringdemo.product.dto.ProductRequest;
import com.monitoringdemo.product.dto.ProductResponse;
import com.monitoringdemo.product.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductResponse create(ProductRequest request) {
        Product saved = productRepository.save(
                new Product(request.name(), request.price(), request.stockQuantity()));
        log.info("Đã tạo product id={} name={}", saved.getId(), saved.getName());
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(getOrThrow(id));
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getOrThrow(id);
        product.setName(request.name());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        log.info("Đã cập nhật product id={} name={}", product.getId(), product.getName());
        return ProductResponse.from(product);
    }

    public ProductResponse reserveStock(Long id, int quantity) {
        Product product = getOrThrow(id);
        product.decreaseStock(quantity);
        log.info("Đã trừ {} tồn kho của product id={}, còn lại {}", quantity, id, product.getStockQuantity());
        return ProductResponse.from(product);
    }

    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy product id=" + id);
        }
        productRepository.deleteById(id);
        log.info("Đã xoá product id={}", id);
    }

    private Product getOrThrow(Long id) {
        return productRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy product id=" + id));
    }
}
