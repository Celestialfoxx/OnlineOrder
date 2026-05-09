package com.example.onlineorder.repository;

import com.example.onlineorder.entity.OrderEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface OrderRepository extends ListCrudRepository<OrderEntity, Long> {
    @Modifying
    @Query("UPDATE orders SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :orderId")
    void updateStatus(Long orderId, String status);
}
