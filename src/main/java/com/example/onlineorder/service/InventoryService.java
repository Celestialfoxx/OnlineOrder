package com.example.onlineorder.service;

import com.example.onlineorder.entity.MenuItemEntity;
import com.example.onlineorder.exception.InventoryNotAvailableException;
import com.example.onlineorder.repository.MenuItemRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private static final int MAX_STOCK_DEDUCTION_ATTEMPTS = 3;

    private final MenuItemRepository menuItemRepository;

    public InventoryService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    // 扣库存的重试机制：每次扣库存前都重新读取一次 menuItem 的最新信息，确保拿到最新的 version 和 stock 来进行扣库存操作。
    // 如果扣库存失败（updatedRows == 0），说明可能是 version 不对了，或者 stock 不够了，这时再重新读取一次 menuItem 的最新信息来进行下一轮尝试。
    // 最多尝试 MAX_STOCK_DEDUCTION_ATTEMPTS 次，如果还是失败了，就抛出库存不可用的异常。
    public MenuItemEntity deductStock(Long menuItemId, Integer quantity) {
        MenuItemEntity latestMenuItem = menuItemRepository.findById(menuItemId).get();

        for (int attempt = 1; attempt <= MAX_STOCK_DEDUCTION_ATTEMPTS; attempt++) {
            if (latestMenuItem.stock() < quantity) {
                throw new InventoryNotAvailableException("Insufficient stock for item: " + latestMenuItem.name());
            }

            // Optimistic locking to deduct stock, ensuring that if stock is insufficient or has been modified by another transaction, the checkout will fail gracefully.
            int updatedRows = menuItemRepository.deductStockWithVersion(
                    latestMenuItem.id(),
                    quantity,
                    latestMenuItem.version()
            );

            if (updatedRows == 1) {
                return latestMenuItem;
            }

            latestMenuItem = menuItemRepository.findById(menuItemId).get();
        }

        throw new InventoryNotAvailableException("Inventory changed during checkout, please retry");
    }
}
