package com.example.onlineorder.controller;

import com.example.onlineorder.entity.CustomerEntity;
import com.example.onlineorder.model.AddToCartBody;
import com.example.onlineorder.model.CartDto;
import com.example.onlineorder.service.CartService;
import com.example.onlineorder.service.CustomerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartController {


    private final CartService cartService;
    private final CustomerService customerService;


    public CartController(CartService cartService, CustomerService customerService) {
        this.cartService = cartService;
        this.customerService = customerService;
    }


    @GetMapping("/cart")
    public CartDto getCart(@AuthenticationPrincipal User user) {
        //Authentication的annotation在这里会自动告诉function发送请求的user是谁
        //spring boot会根据这个user去找对应的信息
        CustomerEntity customer = customerService.getCustomerByEmail(user.getUsername());
        return cartService.getCart(customer.id());
    }


    @PostMapping("/cart")
    public void addToCart(@AuthenticationPrincipal User user, @RequestBody AddToCartBody body) {
        CustomerEntity customer = customerService.getCustomerByEmail(user.getUsername());
        cartService.addMenuItemToCart(customer.id(), body.menuId());
    }


    @PostMapping("/cart/checkout")
    public void checkout(@AuthenticationPrincipal User user) {
        CustomerEntity customer = customerService.getCustomerByEmail(user.getUsername());
        cartService.clearCart(customer.id());
    }
}

