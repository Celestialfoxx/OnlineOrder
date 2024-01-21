package com.example.onlineorder.service;

import com.example.onlineorder.entity.CartEntity;
import com.example.onlineorder.entity.MenuItemEntity;
import com.example.onlineorder.entity.OrderItemEntity;
import com.example.onlineorder.model.CartDto;
import com.example.onlineorder.model.OrderItemDto;
import com.example.onlineorder.repository.CartRepository;
import com.example.onlineorder.repository.MenuItemRepository;
import com.example.onlineorder.repository.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CartService {


    private final CartRepository cartRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderItemRepository orderItemRepository;


    public CartService(
            CartRepository cartRepository,
            MenuItemRepository menuItemRepository,
            OrderItemRepository orderItemRepository) {
        this.cartRepository = cartRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderItemRepository = orderItemRepository;
    }


    @Transactional
    public void addMenuItemToCart(long customerId, long menuItemId) {
        CartEntity cart = cartRepository.getByCustomerId(customerId);
        MenuItemEntity menuItem = menuItemRepository.findById(menuItemId).get();
        OrderItemEntity orderItem = orderItemRepository.findByCartIdAndMenuItemId(cart.id(), menuItem.id());


        Long orderItemId;
        int quantity;


        if (orderItem == null) {
            orderItemId = null;
            quantity = 1;
        } else {
            orderItemId = orderItem.id();
            quantity = orderItem.quantity() + 1;
        }
        OrderItemEntity newOrderItem = new OrderItemEntity(orderItemId, menuItemId, cart.id(), menuItem.price(), quantity);
        orderItemRepository.save(newOrderItem);
        cartRepository.updateTotalPrice(cart.id(), cart.totalPrice() + menuItem.price());
    }


    public CartDto getCart(Long customerId) {
        CartEntity cart = cartRepository.getByCustomerId(customerId);
        List<OrderItemEntity> orderItems = orderItemRepository.getAllByCartId(cart.id());
        List<OrderItemDto> orderItemDtos = getOrderItemDtos(orderItems);
        return new CartDto(cart, orderItemDtos);
    }


    @Transactional
    public void clearCart(Long customerId) {
        CartEntity cartEntity = cartRepository.getByCustomerId(customerId);
        orderItemRepository.deleteByCartId(cartEntity.id());
        cartRepository.updateTotalPrice(cartEntity.id(), 0.0);
    }


    private List<OrderItemDto> getOrderItemDtos(List<OrderItemEntity> orderItems) {
        Set<Long> menuItemIds = new HashSet<>();
        for (OrderItemEntity orderItem : orderItems) {
            menuItemIds.add(orderItem.menuItemId());
        }
        List<MenuItemEntity> menuItems = menuItemRepository.findAllById(menuItemIds);
        Map<Long, MenuItemEntity> menuItemMap = new HashMap<>();
        for (MenuItemEntity menuItem : menuItems) {
            menuItemMap.put(menuItem.id(), menuItem);
        }
        List<OrderItemDto> orderItemDtos = new ArrayList<>();
        for (OrderItemEntity orderItem : orderItems) {
            MenuItemEntity menuItem = menuItemMap.get(orderItem.menuItemId());
            OrderItemDto orderItemDto = new OrderItemDto(orderItem, menuItem);
            orderItemDtos.add(orderItemDto);
        }
        return orderItemDtos;
    }
}

