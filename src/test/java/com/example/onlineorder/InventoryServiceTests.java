package com.example.onlineorder;

import com.example.onlineorder.entity.MenuItemEntity;
import com.example.onlineorder.exception.InventoryNotAvailableException;
import com.example.onlineorder.repository.MenuItemRepository;
import com.example.onlineorder.service.InventoryService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class InventoryServiceTests {

    @Mock
    private MenuItemRepository menuItemRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setup() {
        inventoryService = new InventoryService(menuItemRepository);
    }

    @Test
    void deductStock_whenStockIsAvailable_shouldDeductSuccessfully() {
        MenuItemEntity menuItem = new MenuItemEntity(4L, 5L, "Burger", "", 10.0, "", 100, 0);

        Mockito.when(menuItemRepository.findById(4L)).thenReturn(Optional.of(menuItem));
        Mockito.when(menuItemRepository.deductStockWithVersion(4L, 2, 0)).thenReturn(1);

        MenuItemEntity result = inventoryService.deductStock(4L, 2);

        Assertions.assertEquals(menuItem.id(), result.id());
        Mockito.verify(menuItemRepository).deductStockWithVersion(4L, 2, 0);
    }

    @Test
    void deductStock_whenStockIsInsufficient_shouldThrowExceptionWithoutDeducting() {
        MenuItemEntity menuItem = new MenuItemEntity(4L, 5L, "Burger", "", 10.0, "", 1, 0);

        Mockito.when(menuItemRepository.findById(4L)).thenReturn(Optional.of(menuItem));

        Assertions.assertThrows(
                InventoryNotAvailableException.class,
                () -> inventoryService.deductStock(4L, 2)
        );

        Mockito.verify(menuItemRepository, Mockito.never())
                .deductStockWithVersion(Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    void deductStock_whenVersionConflictButStockAvailable_shouldRetryAndSucceed() {
        MenuItemEntity staleMenuItem = new MenuItemEntity(4L, 5L, "Burger", "", 10.0, "", 100, 0);
        MenuItemEntity latestMenuItem = new MenuItemEntity(4L, 5L, "Burger", "", 10.0, "", 98, 1);

        Mockito.when(menuItemRepository.findById(4L)).thenReturn(Optional.of(staleMenuItem), Optional.of(latestMenuItem));
        Mockito.when(menuItemRepository.deductStockWithVersion(4L, 2, 0)).thenReturn(0);
        Mockito.when(menuItemRepository.deductStockWithVersion(4L, 2, 1)).thenReturn(1);

        MenuItemEntity result = inventoryService.deductStock(4L, 2);

        Assertions.assertEquals(latestMenuItem.version(), result.version());
        Mockito.verify(menuItemRepository).deductStockWithVersion(4L, 2, 0);
        Mockito.verify(menuItemRepository).deductStockWithVersion(4L, 2, 1);
    }
}
