package com.slotsync.service;

import com.slotsync.entity.RefreshToken;
import com.slotsync.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Raw refresh token values are handed to the client once and never persisted.
 * We store a SHA-256 digest as an indexed lookup key rather than bcrypt: a
 * 384-bit random token has no brute-forceable keyspace, so a fast deterministic
 * hash gives an O(1) indexed lookup instead of scanning every stored token
 * (which a per-token-salted bcrypt hash would force). On refresh, the old
 * token is revoked and a new one issued (rotation) rather than reused.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final long refreshTtlDays;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @Value("${slotsync.jwt.refresh-ttl-days:7}") long refreshTtlDays) {
        this.repository = repository;
        this.refreshTtlDays = refreshTtlDays;
    }

    public String issue(Long userId) {
        String raw = randomToken();
        RefreshToken entity = new RefreshToken(
                userId,
                sha256(raw),
                Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS)
        );
        repository.save(entity);
        return raw;
    }

    /** Validates the raw token, revokes it, and returns the associated userId if still valid. */
    public Optional<Long> rotate(String rawToken) {
        return repository.findByTokenHash(sha256(rawToken))
                .filter(rt -> !rt.isRevoked() && rt.getExpiresAt().isAfter(Instant.now()))
                .map(rt -> {
                    rt.setRevoked(true);
                    repository.save(rt);
                    return rt.getUserId();
                });
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
