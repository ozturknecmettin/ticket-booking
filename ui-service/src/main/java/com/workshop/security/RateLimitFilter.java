package com.workshop.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting for public auth endpoints.
 * Uses Bucket4j in-memory token buckets — no external cache needed.
 *
 * Limits:
 *   POST /auth/login    → 5 requests / minute / IP
 *   POST /auth/register → 3 requests / minute / IP
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Separate bucket maps per endpoint path
    private final Map<String, Bucket> loginBuckets    = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path   = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method) && "/auth/login".equals(path)) {
            if (!tryConsume(loginBuckets, ip(request), 5)) {
                reject(response);
                return;
            }
        } else if ("POST".equalsIgnoreCase(method) && "/auth/register".equals(path)) {
            if (!tryConsume(registerBuckets, ip(request), 3)) {
                reject(response);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean tryConsume(Map<String, Bucket> buckets, String ip, int capacity) {
        Bucket bucket = buckets.computeIfAbsent(ip, k -> buildBucket(capacity));
        return bucket.tryConsume(1);
    }

    private Bucket buildBucket(int capacityPerMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacityPerMinute,
                        Refill.greedy(capacityPerMinute, Duration.ofMinutes(1))))
                .build();
    }

    private String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response) throws IOException {
        log.warn("Rate limit exceeded for auth endpoint");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"error\":\"Too many requests \u2014 please try again later\"}");
    }
}
