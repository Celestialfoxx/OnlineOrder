package com.example.onlineorder.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class RateLimitService {

    private static final DateTimeFormatter MINUTE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    // redisTemplate 是 Spring 提供的操作 Redis 的工具对象，封装了很多常用的 Redis 操作方法。
    // StringRedisTemplate 适合 key 和 value 都是 String 的场景；这里的 value 虽然逻辑上是请求次数 Long，
    // 但 Redis 实际存的是字符串形式的数字，比如 "3"，INCR 会把它当数字递增，并把结果作为 Long 返回给 Java。
    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String scope, String identity, int limitPerMinute) {
        String key = buildKey(scope, identity);

        // Redis 的 INCR 是原子操作，适合在高并发请求下安全地统计当前分钟的访问次数。
        Long currentCount = redisTemplate.opsForValue().increment(key);

        // 第一次创建这个 key 时设置 1 分钟过期，避免 Redis 里长期堆积历史限流计数。
        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        // currentCount 超过 limitPerMinute 时，说明这个用户/IP 在当前分钟请求过多，需要拒绝。
        return currentCount != null && currentCount <= limitPerMinute;
    }

    private String buildKey(String scope, String identity) {
        // key 按“业务范围 + 用户/IP + 当前分钟”分组，实现固定窗口限流。
        String currentMinute = LocalDateTime.now().format(MINUTE_FORMATTER);
        return "rate_limit:" + scope + ":" + identity + ":" + currentMinute;
    }
}
