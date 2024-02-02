package com.example.onlineorder.service;

import com.example.onlineorder.entity.CartEntity;
import com.example.onlineorder.entity.MenuItemEntity;
import com.example.onlineorder.entity.OrderItemEntity;
import com.example.onlineorder.model.CartDto;
import com.example.onlineorder.model.OrderItemDto;
import com.example.onlineorder.repository.CartRepository;
import com.example.onlineorder.repository.MenuItemRepository;
import com.example.onlineorder.repository.OrderItemRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    //写入操作时需要按照key来删除cache中的条目， 再重新储存再cache中，来保持数据一致性
    //如果写入操作只有一个parameter， 默认那个为查找要删除对象的key； 否则需要指定， 如下
    @CacheEvict(cacheNames = "cart", key = "#customerId")
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

    @Cacheable("cart")
    public CartDto getCart(Long customerId) {
        //获取该customer的cart
        CartEntity cart = cartRepository.getByCustomerId(customerId);

        //获取所有在该cart中的items
        List<OrderItemEntity> orderItems = orderItemRepository.getAllByCartId(cart.id());

        //调用getOrderItemDtos方法，返回orderItemDto，仅传给前端关于orderItems有用的信息

        List<OrderItemDto> orderItemDtos = getOrderItemDtos(orderItems);
        return new CartDto(cart, orderItemDtos);
    }

    //这里默认按照customerId来删除cache里的条目， 因为只有customerId一个parameter
    @CacheEvict(cacheNames = "cart")
    @Transactional
    public void clearCart(Long customerId) {
        CartEntity cartEntity = cartRepository.getByCustomerId(customerId);
        orderItemRepository.deleteByCartId(cartEntity.id());
        cartRepository.updateTotalPrice(cartEntity.id(), 0.0);
    }


    private List<OrderItemDto> getOrderItemDtos(List<OrderItemEntity> orderItems) {
        //orderItemDto依赖于orderItemEntity和menuItemEntity
        //orderItemEntity在上面已经获得
        //这里根据orderItem的id找到所有menuItem的信息
        //返回OrderItemDto
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

