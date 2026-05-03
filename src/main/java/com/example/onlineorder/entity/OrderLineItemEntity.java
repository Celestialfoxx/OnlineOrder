package com.example.onlineorder.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_line_items")
public record OrderLineItemEntity(
        @Id Long id,
        Long orderId,
        Long menuItemId,
        Long restaurantId,
        String itemNameSnapshot,
        Double unitPrice,
        Integer quantity,
        Double lineTotal
) {
}
