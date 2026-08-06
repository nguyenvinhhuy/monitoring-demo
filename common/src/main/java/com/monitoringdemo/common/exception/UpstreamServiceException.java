package com.monitoringdemo.common.exception;

/** Ném ra khi gọi sang service khác (vd order-service gọi user-service) thất bại — map sang HTTP 502. */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
