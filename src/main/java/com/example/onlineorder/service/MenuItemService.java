package com.example.onlineorder.service;

import com.example.onlineorder.entity.MenuItemEntity;
import com.example.onlineorder.repository.MenuItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;

@Service
public class MenuItemService {


    private final MenuItemRepository menuItemRepository;


    public MenuItemService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    // 如果以后加了餐厅/菜单修改接口，写操作后要清理 restaurantMenu::<restaurantId>
    @Cacheable(cacheNames = "restaurantMenu", key = "#restaurantId")
    public List<MenuItemEntity> getMenuItemsByRestaurantId(long restaurantId) {
        return menuItemRepository.getByRestaurantId(restaurantId);
    }


    public MenuItemEntity getMenuItemById(long id) {
        return menuItemRepository.findById(id).get();
    }


}

