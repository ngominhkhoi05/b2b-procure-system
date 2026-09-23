package com.b2bprocure.system.zalopay.client;

/**
 * Exception thrown when ZaloPay API call fails.
 */
public class ZaloPayException extends RuntimeException {

    public ZaloPayException(String message) {
        super(message);
    }

    public ZaloPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
