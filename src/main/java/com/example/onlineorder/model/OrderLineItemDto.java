package com.example.onlineorder.model;

import com.example.onlineorder.entity.OrderLineItemEntity;

public record OrderLineItemDto(
        Long id,
        Long menuItemId,
        Long restaurantId,
        String itemNameSnapshot,
        Double unitPrice,
        Integer quantity,
        Double lineTotal
) {
    public OrderLineItemDto(OrderLineItemEntity entity) {
        this(
                entity.id(),
                entity.menuItemId(),
                entity.restaurantId(),
                entity.itemNameSnapshot(),
                entity.unitPrice(),
                entity.quantity(),
                entity.lineTotal()
        );
    }
}
