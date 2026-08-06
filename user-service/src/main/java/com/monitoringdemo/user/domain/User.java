package com.monitoringdemo.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Không dùng {@code @Data}/{@code @EqualsAndHashCode} — sinh equals/hashCode/toString tự động
 * trên JPA entity dễ vỡ khi Hibernate lazy-load proxy. Chỉ lấy phần an toàn: getter/setter/no-arg
 * constructor. Constructor có tham số giữ nguyên viết tay vì đó là logic khởi tạo có ý nghĩa,
 * không phải boilerplate.
 */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String fullName;

    @Setter
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public User(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }
}
