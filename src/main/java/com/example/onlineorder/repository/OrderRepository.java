package com.example.onlineorder.repository;

import com.example.onlineorder.entity.OrderEntity;
import org.springframework.data.repository.ListCrudRepository;

public interface OrderRepository extends ListCrudRepository<OrderEntity, Long> {
}
