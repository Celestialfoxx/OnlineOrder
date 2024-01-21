package com.example.onlineorder.controller;

import com.example.onlineorder.model.AddToCartBody;
import com.example.onlineorder.model.CartDto;
import com.example.onlineorder.service.CartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartController {


    private final CartService cartService;


    public CartController(CartService cartService) {
        this.cartService = cartService;
    }


    @GetMapping("/cart")
    public CartDto getCart() {
        return cartService.getCart(1L);
    }


    @PostMapping("/cart")
    public void addToCart(@RequestBody AddToCartBody body) {
        cartService.addMenuItemToCart(1L, body.menuId());
    }


    @PostMapping("/cart/checkout")
    public void checkout() {
        cartService.clearCart(1L);
    }
}

