package com.example.onlineorder;

import com.example.onlineorder.entity.CartEntity;
import com.example.onlineorder.entity.IdempotencyRecordEntity;
import com.example.onlineorder.entity.MenuItemEntity;
import com.example.onlineorder.entity.OrderEntity;
import com.example.onlineorder.entity.OrderItemEntity;
import com.example.onlineorder.entity.OrderLineItemEntity;
import com.example.onlineorder.entity.OrderStatus;
import com.example.onlineorder.event.OrderCreatedEvent;
import com.example.onlineorder.event.OrderEventProducer;
import com.example.onlineorder.exception.InventoryNotAvailableException;
import com.example.onlineorder.model.CheckoutResponse;
import com.example.onlineorder.repository.CartRepository;
import com.example.onlineorder.repository.IdempotencyRecordRepository;
import com.example.onlineorder.repository.MenuItemRepository;
import com.example.onlineorder.repository.OrderItemRepository;
import com.example.onlineorder.repository.OrderLineItemRepository;
import com.example.onlineorder.repository.OrderRepository;
import com.example.onlineorder.service.InventoryService;
import com.example.onlineorder.service.OrderService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTests {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderLineItemRepository orderLineItemRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private OrderEventProducer orderEventProducer;

    private OrderService orderService;

    @BeforeEach
    void setup() {
        InventoryService inventoryService = new InventoryService(menuItemRepository);
        orderService = new OrderService(
                cartRepository,
                orderItemRepository,
                orderRepository,
                orderLineItemRepository,
                idempotencyRecordRepository,
                orderEventProducer,
                inventoryService
        );
    }

    @Test
    void checkout_whenCartHasItems_shouldCreateOrderAndClearCart() {
        long customerId = 1L;
        long cartId = 2L;
        String idempotencyKey = "checkout-key";

        CartEntity cart = new CartEntity(cartId, customerId, 20.0);
        OrderItemEntity cartItem = new OrderItemEntity(3L, 4L, cartId, 10.0, 2);
        MenuItemEntity menuItem = new MenuItemEntity(4L, 5L, "Burger", "", 10.0, "", 100, 0);
        OrderEntity savedOrder = new OrderEntity(6L, customerId, OrderStatus.CREATED, 20.0, LocalDateTime.now(), LocalDateTime.now());
        OrderLineItemEntity savedLineItem = new OrderLineItemEntity(7L, 6L, 4L, 5L, "Burger", 10.0, 2, 20.0);
        IdempotencyRecordEntity savedRecord = new IdempotencyRecordEntity(
                8L,
                idempotencyKey,
                customerId,
                "/cart/checkout",
                "CHECKOUT",
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24)
        );

        Mockito.when(idempotencyRecordRepository.findByCustomerIdAndIdempotencyKeyAndOperationType(
                customerId,
                idempotencyKey,
                "CHECKOUT"
        )).thenReturn(null);
        Mockito.when(cartRepository.getByCustomerId(customerId)).thenReturn(cart);
        Mockito.when(orderItemRepository.getAllByCartId(cartId)).thenReturn(List.of(cartItem));
        Mockito.when(idempotencyRecordRepository.save(Mockito.any(IdempotencyRecordEntity.class))).thenReturn(savedRecord);
        Mockito.when(orderRepository.save(Mockito.any(OrderEntity.class))).thenReturn(savedOrder);
        Mockito.when(menuItemRepository.findById(4L)).thenReturn(Optional.of(menuItem));
        Mockito.when(menuItemRepository.deductStockWithVersion(4L, 2, 0)).thenReturn(1);
        Mockito.when(orderLineItemRepository.save(Mockito.any(OrderLineItemEntity.class))).thenReturn(savedLineItem);

        CheckoutResponse response = orderService.checkout(customerId, idempotencyKey);

        Assertions.assertEquals(savedOrder.id(), response.orderId());
        Assertions.assertEquals(OrderStatus.CREATED, response.status());
        Assertions.assertEquals(1, response.orderItems().size());
        Mockito.verify(orderItemRepository).deleteByCartId(cartId);
        Mockito.verify(cartRepository).updateTotalPrice(cartId, 0.0);
        Mockito.verify(idempotencyRecordRepository, Mockito.times(2)).save(Mockito.any(IdempotencyRecordEntity.class));
        Mockito.verify(menuItemRepository).deductStockWithVersion(4L, 2, 0);
        Mockito.verify(orderEventProducer).publishOrderCreated(Mockito.any(OrderCreatedEvent.class));
    }

    @Test
    void checkout_whenIdempotencyRecordAlreadyCompleted_shouldReturnExistingOrder() {
        long customerId = 1L;
        long orderId = 6L;
        String idempotencyKey = "checkout-key";
        LocalDateTime now = LocalDateTime.now();
        IdempotencyRecordEntity existingRecord = new IdempotencyRecordEntity(
                8L,
                idempotencyKey,
                customerId,
                "/cart/checkout",
                "CHECKOUT",
                orderId,
                201,
                now,
                now.plusHours(24)
        );
        OrderEntity order = new OrderEntity(orderId, customerId, OrderStatus.CREATED, 20.0, now, now);
        OrderLineItemEntity lineItem = new OrderLineItemEntity(7L, orderId, 4L, 5L, "Burger", 10.0, 2, 20.0);

        Mockito.when(idempotencyRecordRepository.findByCustomerIdAndIdempotencyKeyAndOperationType(
                customerId,
                idempotencyKey,
                "CHECKOUT"
        )).thenReturn(existingRecord);
        Mockito.when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        Mockito.when(orderLineItemRepository.findByOrderId(orderId)).thenReturn(List.of(lineItem));

        CheckoutResponse response = orderService.checkout(customerId, idempotencyKey);

        Assertions.assertEquals(orderId, response.orderId());
        Assertions.assertEquals(1, response.orderItems().size());
        Mockito.verify(orderRepository, Mockito.never()).save(Mockito.any(OrderEntity.class));
        Mockito.verify(orderItemRepository, Mockito.never()).deleteByCartId(Mockito.anyLong());
        Mockito.verify(cartRepository, Mockito.never()).updateTotalPrice(Mockito.anyLong(), Mockito.anyDouble());
        Mockito.verify(orderEventProducer, Mockito.never()).publishOrderCreated(Mockito.any(OrderCreatedEvent.class));
    }

    @Test
    void checkout_whenInventoryIsInsufficient_shouldThrowExceptionAndNotClearCart() {
        long customerId = 1L;
        long cartId = 2L;
        String idempotencyKey = "checkout-key";

        CartEntity cart = new CartEntity(cartId, customerId, 20.0);
        OrderItemEntity cartItem = new OrderItemEntity(3L, 4L, cartId, 10.0, 2);
        MenuItemEntity menuItem = new MenuItemEntity(4L, 5L, "Burger", "", 10.0, "", 1, 0);
        OrderEntity savedOrder = new OrderEntity(6L, customerId, OrderStatus.CREATED, 20.0, LocalDateTime.now(), LocalDateTime.now());
        IdempotencyRecordEntity savedRecord = new IdempotencyRecordEntity(
                8L,
                idempotencyKey,
                customerId,
                "/cart/checkout",
                "CHECKOUT",
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24)
        );

        Mockito.when(idempotencyRecordRepository.findByCustomerIdAndIdempotencyKeyAndOperationType(
                customerId,
                idempotencyKey,
                "CHECKOUT"
        )).thenReturn(null);
        Mockito.when(cartRepository.getByCustomerId(customerId)).thenReturn(cart);
        Mockito.when(orderItemRepository.getAllByCartId(cartId)).thenReturn(List.of(cartItem));
        Mockito.when(idempotencyRecordRepository.save(Mockito.any(IdempotencyRecordEntity.class))).thenReturn(savedRecord);
        Mockito.when(orderRepository.save(Mockito.any(OrderEntity.class))).thenReturn(savedOrder);
        Mockito.when(menuItemRepository.findById(4L)).thenReturn(Optional.of(menuItem));

        Assertions.assertThrows(
                InventoryNotAvailableException.class,
                () -> orderService.checkout(customerId, idempotencyKey)
        );

        Mockito.verify(menuItemRepository, Mockito.never()).deductStockWithVersion(Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(orderLineItemRepository, Mockito.never()).save(Mockito.any(OrderLineItemEntity.class));
        Mockito.verify(orderItemRepository, Mockito.never()).deleteByCartId(Mockito.anyLong());
        Mockito.verify(cartRepository, Mockito.never()).updateTotalPrice(Mockito.anyLong(), Mockito.anyDouble());
        Mockito.verify(orderEventProducer, Mockito.never()).publishOrderCreated(Mockito.any(OrderCreatedEvent.class));
    }

    @Test
    void checkout_whenVersionConflictButStockAvailable_shouldRetryAndCreateOrder() {
        long customerId = 1L;
        long cartId = 2L;
        String idempotencyKey = "checkout-key";

        CartEntity cart = new CartEntity(cartId, customerId, 20.0);
        OrderItemEntity cartItem = new OrderItemEntity(3L, 4L, cartId, 10.0, 2);
        MenuItemEntity staleMenuItem = new MenuItemEntity(4L, 5L, "Burger", "", 10.0, "", 100, 0);
        MenuItemEntity latestMenuItem = new MenuItemEntity(4L, 5L, "Burger", "", 10.0, "", 98, 1);
        OrderEntity savedOrder = new OrderEntity(6L, customerId, OrderStatus.CREATED, 20.0, LocalDateTime.now(), LocalDateTime.now());
        OrderLineItemEntity savedLineItem = new OrderLineItemEntity(7L, 6L, 4L, 5L, "Burger", 10.0, 2, 20.0);
        IdempotencyRecordEntity savedRecord = new IdempotencyRecordEntity(
                8L,
                idempotencyKey,
                customerId,
                "/cart/checkout",
                "CHECKOUT",
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(24)
        );

        Mockito.when(idempotencyRecordRepository.findByCustomerIdAndIdempotencyKeyAndOperationType(
                customerId,
                idempotencyKey,
                "CHECKOUT"
        )).thenReturn(null);
        Mockito.when(cartRepository.getByCustomerId(customerId)).thenReturn(cart);
        Mockito.when(orderItemRepository.getAllByCartId(cartId)).thenReturn(List.of(cartItem));
        Mockito.when(idempotencyRecordRepository.save(Mockito.any(IdempotencyRecordEntity.class))).thenReturn(savedRecord);
        Mockito.when(orderRepository.save(Mockito.any(OrderEntity.class))).thenReturn(savedOrder);
        Mockito.when(menuItemRepository.findById(4L)).thenReturn(Optional.of(staleMenuItem), Optional.of(latestMenuItem));
        Mockito.when(menuItemRepository.deductStockWithVersion(4L, 2, 0)).thenReturn(0);
        Mockito.when(menuItemRepository.deductStockWithVersion(4L, 2, 1)).thenReturn(1);
        Mockito.when(orderLineItemRepository.save(Mockito.any(OrderLineItemEntity.class))).thenReturn(savedLineItem);

        CheckoutResponse response = orderService.checkout(customerId, idempotencyKey);

        Assertions.assertEquals(savedOrder.id(), response.orderId());
        Mockito.verify(menuItemRepository).deductStockWithVersion(4L, 2, 0);
        Mockito.verify(menuItemRepository).deductStockWithVersion(4L, 2, 1);
        Mockito.verify(orderItemRepository).deleteByCartId(cartId);
        Mockito.verify(cartRepository).updateTotalPrice(cartId, 0.0);
        Mockito.verify(orderEventProducer).publishOrderCreated(Mockito.any(OrderCreatedEvent.class));
    }
}
