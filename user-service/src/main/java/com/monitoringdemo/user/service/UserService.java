package com.monitoringdemo.user.service;

import com.monitoringdemo.common.exception.ResourceNotFoundException;
import com.monitoringdemo.user.domain.User;
import com.monitoringdemo.user.dto.UserRequest;
import com.monitoringdemo.user.dto.UserResponse;
import com.monitoringdemo.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserResponse create(UserRequest request) {
        User saved = userRepository.save(new User(request.fullName(), request.email()));
        log.info("Đã tạo user id={} email={}", saved.getId(), saved.getEmail());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + id));
        return UserResponse.from(user);
    }

    public UserResponse update(Long id, UserRequest request) {
        User user = userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + id));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        log.info("Đã cập nhật user id={} email={}", user.getId(), user.getEmail());
        return UserResponse.from(user);
    }

    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy user id=" + id);
        }
        userRepository.deleteById(id);
        log.info("Đã xoá user id={}", id);
    }
}
