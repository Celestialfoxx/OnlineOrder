package com.example.onlineorder.service;

import com.example.onlineorder.entity.CartEntity;
import com.example.onlineorder.entity.MenuItemEntity;
import com.example.onlineorder.entity.OrderEntity;
import com.example.onlineorder.entity.OrderItemEntity;
import com.example.onlineorder.entity.OrderLineItemEntity;
import com.example.onlineorder.entity.OrderStatus;
import com.example.onlineorder.exception.CheckoutInProgressException;
import com.example.onlineorder.exception.EmptyCartException;
import com.example.onlineorder.model.CheckoutResponse;
import com.example.onlineorder.model.OrderLineItemDto;
import com.example.onlineorder.repository.CartRepository;
import com.example.onlineorder.repository.MenuItemRepository;
import com.example.onlineorder.repository.OrderItemRepository;
import com.example.onlineorder.repository.OrderLineItemRepository;
import com.example.onlineorder.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.onlineorder.entity.IdempotencyRecordEntity;
import com.example.onlineorder.repository.IdempotencyRecordRepository;
import com.example.onlineorder.event.OrderCreatedEvent;
import com.example.onlineorder.event.OrderEventProducer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


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
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OrderEventProducer orderEventProducer;



    public OrderService(
            CartRepository cartRepository,
            OrderItemRepository orderItemRepository,
            MenuItemRepository menuItemRepository,
            OrderRepository orderRepository,
            OrderLineItemRepository orderLineItemRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            OrderEventProducer orderEventProducer) {
        this.cartRepository = cartRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.orderLineItemRepository = orderLineItemRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.orderEventProducer = orderEventProducer;
    }

    @Transactional
    public CheckoutResponse checkout(Long customerId, String idempotencyKey) {
        String operationType = "CHECKOUT";
        IdempotencyRecordEntity existingRecord = idempotencyRecordRepository.findByCustomerIdAndIdempotencyKeyAndOperationType(
                customerId, idempotencyKey, operationType
        );

        // 如果已经有一条记录，并且记录里有 createdOrderId，说明之前的请求已经成功创建了订单，直接返回订单信息。
        if (existingRecord != null && existingRecord.createdOrderId() != null) {
            return getCheckoutResponse(existingRecord.createdOrderId());
        }

        // 如果已经有一条记录，但记录里没有 createdOrderId，说明之前的请求还在处理中，抛出异常提示用户请求正在处理中。
        if (existingRecord != null) {
            throw new CheckoutInProgressException("Checkout is already in progress");
        }

        CartEntity cart = cartRepository.getByCustomerId(customerId);
        List<OrderItemEntity> cartItems = orderItemRepository.getAllByCartId(cart.id());

        if (cartItems.isEmpty()) {
            throw new EmptyCartException("Cannot checkout an empty cart");
        }

        LocalDateTime now = LocalDateTime.now();

        // 先保存一条未完成的幂等记录来“占住”这个 key，避免并发重复请求同时创建多张订单。
        // 订单创建完成后，再把 createdOrderId 更新回同一条记录，方便后续重复请求返回同一张订单。
        IdempotencyRecordEntity idempotencyRecord = new IdempotencyRecordEntity(
            null,
            idempotencyKey,
            customerId,
            "/cart/checkout",
            operationType,
            null,
            null,
            now,
            now.plusHours(24)
        );
        IdempotencyRecordEntity savedIdempotencyRecord = idempotencyRecordRepository.save(idempotencyRecord);

        // Create Order
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

        // 订单创建出来后，再完整更新幂等记录，确保无论后续请求是命中未完成记录还是完整记录，都能正确返回订单信息。
        IdempotencyRecordEntity completedRecord = new IdempotencyRecordEntity(
                savedIdempotencyRecord.id(),
                savedIdempotencyRecord.idempotencyKey(),
                savedIdempotencyRecord.customerId(),
                savedIdempotencyRecord.requestPath(),
                savedIdempotencyRecord.operationType(),
                savedOrder.id(),
                201,
                savedIdempotencyRecord.createdAt(),
                savedIdempotencyRecord.expiredAt()
        );
        idempotencyRecordRepository.save(completedRecord);
        orderEventProducer.publishOrderCreated(
                OrderCreatedEvent.from(savedOrder.id(), savedOrder.customerId(), savedOrder.totalAmount())
        );


        return new CheckoutResponse(savedOrder, orderLineItemDtos);
    }

    public CheckoutResponse getOrderForCustomer(Long orderId, Long customerId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!order.customerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this order");
        }

        return getCheckoutResponse(order);
    }

    private CheckoutResponse getCheckoutResponse(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId).get();
        return getCheckoutResponse(order);
    }

    private CheckoutResponse getCheckoutResponse(OrderEntity order) {
        List<OrderLineItemDto> orderLineItems = orderLineItemRepository.findByOrderId(order.id())
                .stream()
                .map(OrderLineItemDto::new)
                .toList();
        return new CheckoutResponse(order, orderLineItems);
    }

    @Transactional
    public void markOrderPaid(Long orderId) {
        orderRepository.updateStatus(orderId, OrderStatus.PAID.name());
    }

    @Transactional
    public void markOrderFailed(Long orderId) {
        orderRepository.updateStatus(orderId, OrderStatus.FAILED.name());
    }
}
