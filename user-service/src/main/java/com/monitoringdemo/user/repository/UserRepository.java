package com.monitoringdemo.user.repository;

import com.monitoringdemo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
