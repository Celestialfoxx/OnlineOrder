package com.example.onlineorder.service;

import com.example.onlineorder.entity.MenuItemEntity;
import com.example.onlineorder.repository.MenuItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuItemService {


    private final MenuItemRepository menuItemRepository;


    public MenuItemService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }


    public List<MenuItemEntity> getMenuItemsByRestaurantId(long restaurantId) {
        return menuItemRepository.getByRestaurantId(restaurantId);
    }


    public MenuItemEntity getMenuItemById(long id) {
        return menuItemRepository.findById(id).get();
    }


}

