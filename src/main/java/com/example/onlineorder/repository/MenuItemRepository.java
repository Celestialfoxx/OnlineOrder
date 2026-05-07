package com.example.onlineorder.repository;

import com.example.onlineorder.entity.MenuItemEntity;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface MenuItemRepository extends ListCrudRepository<MenuItemEntity, Long> {
    List<MenuItemEntity> getByRestaurantId(Long restaurantId);

    @Modifying
    @Query("""
            UPDATE menu_items
            SET stock = stock - :quantity,
                version = version + 1
            WHERE id = :menuItemId
            AND stock >= :quantity
            AND version = :version
            """)
    int deductStockWithVersion(Long menuItemId, Integer quantity, Integer version);

}

