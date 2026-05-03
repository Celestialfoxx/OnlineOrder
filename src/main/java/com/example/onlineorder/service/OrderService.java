package com.example.onlineorder.service;

import com.example.onlineorder.entity.CartEntity;
import com.example.onlineorder.entity.MenuItemEntity;
import com.example.onlineorder.entity.OrderEntity;
import com.example.onlineorder.entity.OrderItemEntity;
import com.example.onlineorder.entity.OrderLineItemEntity;
import com.example.onlineorder.entity.OrderStatus;
import com.example.onlineorder.model.CheckoutResponse;
import com.example.onlineorder.model.OrderLineItemDto;
import com.example.onlineorder.repository.CartRepository;
import com.example.onlineorder.repository.MenuItemRepository;
import com.example.onlineorder.repository.OrderItemRepository;
import com.example.onlineorder.repository.OrderLineItemRepository;
import com.example.onlineorder.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final CartRepository cartRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderRepository orderRepository;
    private final OrderLineItemRepository orderLineItemRepository;

    public OrderService(
            CartRepository cartRepository,
            OrderItemRepository orderItemRepository,
            MenuItemRepository menuItemRepository,
            OrderRepository orderRepository,
            OrderLineItemRepository orderLineItemRepository) {
        this.cartRepository = cartRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.orderLineItemRepository = orderLineItemRepository;
    }

    @Transactional
    public CheckoutResponse checkout(Long customerId) {
        CartEntity cart = cartRepository.getByCustomerId(customerId);
        List<OrderItemEntity> cartItems = orderItemRepository.getAllByCartId(cart.id());

        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        LocalDateTime now = LocalDateTime.now();
        OrderEntity order = new OrderEntity(
                null,
                customerId,
                OrderStatus.CREATED,
                cart.totalPrice(),
                now,
                now
        );
        OrderEntity savedOrder = orderRepository.save(order);

        List<OrderLineItemDto> orderLineItemDtos = new ArrayList<>();
        for (OrderItemEntity cartItem : cartItems) {
            MenuItemEntity menuItem = menuItemRepository.findById(cartItem.menuItemId()).get();
            double lineTotal = cartItem.price() * cartItem.quantity();

            OrderLineItemEntity orderLineItem = new OrderLineItemEntity(
                    null,
                    savedOrder.id(),
                    menuItem.id(),
                    menuItem.restaurantId(),
                    menuItem.name(),
                    cartItem.price(),
                    cartItem.quantity(),
                    lineTotal
            );

            OrderLineItemEntity savedLineItem = orderLineItemRepository.save(orderLineItem);
            orderLineItemDtos.add(new OrderLineItemDto(savedLineItem));
        }

        orderItemRepository.deleteByCartId(cart.id());
        cartRepository.updateTotalPrice(cart.id(), 0.0);

        return new CheckoutResponse(savedOrder, orderLineItemDtos);
    }
}
