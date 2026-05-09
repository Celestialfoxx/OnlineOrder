package com.example.onlineorder.controller;

import com.example.onlineorder.entity.CustomerEntity;
import com.example.onlineorder.model.CheckoutResponse;
import com.example.onlineorder.service.CustomerService;
import com.example.onlineorder.service.OrderService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final CustomerService customerService;

    public OrderController(OrderService orderService, CustomerService customerService) {
        this.orderService = orderService;
        this.customerService = customerService;
    }

    @GetMapping("/orders/{orderId}")
    public CheckoutResponse getOrder(@AuthenticationPrincipal User user, @PathVariable Long orderId) {
        CustomerEntity customer = customerService.getCustomerByEmail(user.getUsername());
        return orderService.getOrderForCustomer(orderId, customer.id());
    }
}
