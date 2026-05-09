package com.example.onlineorder.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("idempotency_records")
public record IdempotencyRecordEntity(
        @Id Long id,
        String idempotencyKey,
        Long customerId,
        String requestPath,
        String operationType,
        Long createdOrderId,
        Integer responseStatus,
        LocalDateTime createdAt,
        LocalDateTime expiredAt
) {
}
