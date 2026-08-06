package com.monitoringdemo.product.repository;

import com.monitoringdemo.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
