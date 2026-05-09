package com.example.onlineorder.repository;

import com.example.onlineorder.entity.OrderLineItemEntity;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface OrderLineItemRepository extends ListCrudRepository<OrderLineItemEntity, Long> {
    List<OrderLineItemEntity> findByOrderId(Long orderId);
}
