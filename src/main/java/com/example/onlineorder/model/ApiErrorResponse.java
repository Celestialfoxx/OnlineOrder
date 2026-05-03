package com.example.onlineorder.model;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        Integer status,
        String error,
        String message,
        LocalDateTime timestamp
) {
}
