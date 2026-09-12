package com.slotsync.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration IDLE_EVICTION_THRESHOLD = Duration.ofMinutes(30);

    private record BucketEntry(Bucket bucket, Instant lastAccess) {
        BucketEntry withTouch() {
            return new BucketEntry(bucket, Instant.now());
        }
    }

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private final boolean trustProxyHeaders;
    private final boolean rateLimitingEnabled;

    public RateLimitFilter(@Value("${slotsync.security.trust-proxy-headers:false}") boolean trustProxyHeaders,
                            @Value("${slotsync.security.rate-limiting-enabled:true}") boolean rateLimitingEnabled) {
        this.trustProxyHeaders = trustProxyHeaders;
        this.rateLimitingEnabled = rateLimitingEnabled;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!rateLimitingEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        RouteLimit routeLimit = classify(request);
        if (routeLimit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = clientIp(request);
        String key = routeLimit.name() + ":" + clientIp;

        BucketEntry entry = buckets.compute(key, (k, existing) -> {
            if (existing != null) {
                return existing.withTouch();
            }
            return new BucketEntry(newBucket(routeLimit), Instant.now());
        });

        if (entry.bucket().tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"message\":\"Too many attempts. Please slow down and try again shortly.\"}");
        }
    }

    private enum RouteLimit { BOOKING, AUTH }

    private RouteLimit classify(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri.equals("/api/bookings")) {
            return RouteLimit.BOOKING;
        }
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/register")) {
            return RouteLimit.AUTH;
        }
        return null;
    }

    private Bucket newBucket(RouteLimit routeLimit) {
        Bandwidth limit = switch (routeLimit) {
            case BOOKING -> Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
            case AUTH -> Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));
        };
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    @Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void evictIdleBuckets() {
        Instant cutoff = Instant.now().minus(IDLE_EVICTION_THRESHOLD);
        buckets.entrySet().removeIf(e -> e.getValue().lastAccess().isBefore(cutoff));
    }
}
