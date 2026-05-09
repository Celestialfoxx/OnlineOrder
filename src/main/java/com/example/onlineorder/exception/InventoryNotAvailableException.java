package com.example.onlineorder.exception;

public class InventoryNotAvailableException extends RuntimeException {

    public InventoryNotAvailableException(String message) {
        super(message);
    }
}
