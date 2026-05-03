package com.example.onlineorder.controller;

import com.example.onlineorder.exception.CheckoutInProgressException;
import com.example.onlineorder.exception.EmptyCartException;
import com.example.onlineorder.model.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
