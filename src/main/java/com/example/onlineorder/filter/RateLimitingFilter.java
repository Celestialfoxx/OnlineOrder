package com.example.onlineorder.filter;

import com.example.onlineorder.model.ApiErrorResponse;
import com.example.onlineorder.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

// @Component 让 Spring 创建并管理这个 filter；OncePerRequestFilter 表示它是 Web filter，Spring Boot 会自动把它注册进每个 HTTP request 的 filter chain。
// OncePerRequestFilter 还能保证同一个 request 只执行一次这个 filter，避免一次请求被重复限流。
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int CHECKOUT_LIMIT_PER_MINUTE = 5;
    private static final int MENU_LIMIT_PER_MINUTE = 60;

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain // FilterChain 是 Tomcat/Spring 在执行 filter 时自动传进来的对象，代表“后面还要继续执行的 filters 和 controller”
    ) throws ServletException, IOException {
        RateLimitRule rule = resolveRule(request);

        // 不在限流范围内的 API 直接放行，不影响其他业务接口。
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = buildIdentity(request, rule.useAuthenticatedUser());
        boolean allowed = rateLimitService.isAllowed(rule.scope(), identity, rule.limitPerMinute());

        if (!allowed) {
            writeTooManyRequestsResponse(response);
            // 不调用 filterChain.doFilter(...) 就表示请求在当前 filter 结束，不会继续进入后续 filter 或 controller。
            return;
        }

        // 请求在当前 filter 通过，继续进入后续的 filters 和 controller，直到最后返回 response 给用户
        filterChain.doFilter(request, response);
    }

    private RateLimitRule resolveRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (HttpMethod.POST.matches(method) && "/cart/checkout".equals(path)) {
            return new RateLimitRule("checkout", CHECKOUT_LIMIT_PER_MINUTE, true);
        }

        if (HttpMethod.GET.matches(method)
                && ("/restaurants/menu".equals(path) || path.matches("^/restaurant/\\d+/menu$"))) {
            return new RateLimitRule("menu", MENU_LIMIT_PER_MINUTE, false);
        }

        return null;
    }

    private String buildIdentity(HttpServletRequest request, boolean useAuthenticatedUser) {
        // checkout 是高风险接口，优先按登录用户限流；如果取不到用户，再退回到 IP 限流。
        if (useAuthenticatedUser) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getName())) {
                return "user:" + authentication.getName();
            }
        }

        return "ip:" + request.getRemoteAddr();
    }

    private void writeTooManyRequestsResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");

        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded. Please try again later.",
                LocalDateTime.now()
        );

        // 这里请求已经被 filter 拦截，不会进入 controller，所以不能依赖 Spring MVC 自动把对象转成 JSON。
        // ObjectMapper 是 Jackson 的 JSON 转换工具，用来手动把 ApiErrorResponse 写进 HTTP response body。
        objectMapper.writeValue(response.getWriter(), body);
    }

    private record RateLimitRule(String scope, int limitPerMinute, boolean useAuthenticatedUser) {
    }
}
