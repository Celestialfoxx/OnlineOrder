package com.example.onlineorder.controller;

import com.example.onlineorder.exception.CheckoutInProgressException;
import com.example.onlineorder.exception.EmptyCartException;
import com.example.onlineorder.exception.InventoryNotAvailableException;
import com.example.onlineorder.model.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

// 全局异常处理器，捕获并处理应用中的特定异常，返回统一的错误响应结构。
// @RestControllerAdvice 是 Spring 提供的全局 REST API 错误处理器，让你不用在每个 controller 里写 try/catch。
@RestControllerAdvice
public class GlobalExceptionHandler {
    /*
    @ExceptionHandler 负责指定“这个异常由这个方法处理”；
    @ResponseStatus 负责指定“返回给前端的 HTTP 状态码”。
    */
    @ExceptionHandler(EmptyCartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleEmptyCart(EmptyCartException exception) {
        return new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                exception.getMessage(),
                LocalDateTime.now()
        );
    }

    @ExceptionHandler(CheckoutInProgressException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleCheckoutInProgress(CheckoutInProgressException exception) {
        return new ApiErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(),
                LocalDateTime.now()
        );
    }

    @ExceptionHandler(InventoryNotAvailableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleInventoryNotAvailable(InventoryNotAvailableException exception) {
        return new ApiErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                exception.getMessage(),
                LocalDateTime.now()
        );
    }
}
