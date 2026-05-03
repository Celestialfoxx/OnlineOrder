package com.example.onlineorder.exception;

public class CheckoutInProgressException extends RuntimeException {
    public CheckoutInProgressException(String message) {
        super(message);
    }
}
