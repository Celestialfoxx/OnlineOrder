package com.example.onlineorder;

import com.example.onlineorder.service.RateLimitService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

@ExtendWith(MockitoExtension.class)
public class RateLimitServiceTests {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setup() {
        rateLimitService = new RateLimitService(redisTemplate);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void isAllowed_whenFirstRequestInWindow_shouldSetTtlAndAllow() {
        Mockito.when(valueOperations.increment(Mockito.startsWith("rate_limit:checkout:user:1:")))
                .thenReturn(1L);

        boolean allowed = rateLimitService.isAllowed("checkout", "user:1", 5);

        Assertions.assertTrue(allowed);
        Mockito.verify(redisTemplate).expire(
                Mockito.startsWith("rate_limit:checkout:user:1:"),
                Mockito.eq(Duration.ofMinutes(1))
        );
    }

    @Test
    void isAllowed_whenRequestCountExceedsLimit_shouldReject() {
        Mockito.when(valueOperations.increment(Mockito.startsWith("rate_limit:checkout:user:1:")))
                .thenReturn(6L);

        boolean allowed = rateLimitService.isAllowed("checkout", "user:1", 5);

        Assertions.assertFalse(allowed);
        Mockito.verify(redisTemplate, Mockito.never())
                .expire(Mockito.anyString(), Mockito.any(Duration.class));
    }
}
