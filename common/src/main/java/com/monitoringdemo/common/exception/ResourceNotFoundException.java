package com.monitoringdemo.common.exception;

/** Ném ra khi một entity tra theo id không tồn tại — GlobalExceptionHandler map sang HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
