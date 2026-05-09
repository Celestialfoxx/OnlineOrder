package com.example.onlineorder.service;

import com.example.onlineorder.event.OrderCreatedEvent;
import com.example.onlineorder.model.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    public PaymentResult processPayment(OrderCreatedEvent event) {
        logger.info("Simulated payment success for orderId={}", event.orderId());
        return PaymentResult.succeeded(event.orderId());
    }
}
