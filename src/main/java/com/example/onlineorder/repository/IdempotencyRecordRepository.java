package com.example.onlineorder.repository;

import com.example.onlineorder.entity.IdempotencyRecordEntity;
import org.springframework.data.repository.ListCrudRepository;

public interface IdempotencyRecordRepository extends ListCrudRepository<IdempotencyRecordEntity, Long> {
    IdempotencyRecordEntity findByCustomerIdAndIdempotencyKeyAndOperationType(
            Long customerId,
            String idempotencyKey,
            String operationType
    );
}
