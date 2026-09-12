package com.slotsync.security;

import com.slotsync.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("this-is-a-test-secret-key-that-is-long-enough-for-hmac-sha", 900000);
    }

    @Test
    void generateAndExtractEmail_roundTrips() {
        String token = jwtService.generateAccessToken("sai@example.com", "USER");

        assertThat(jwtService.extractEmail(token)).isEqualTo("sai@example.com");
        assertThat(jwtService.isTokenValid(token, "sai@example.com")).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForMismatchedEmail() {
        String token = jwtService.generateAccessToken("sai@example.com", "USER");

        assertThat(jwtService.isTokenValid(token, "someone-else@example.com")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForExpiredToken() throws InterruptedException {
        JwtService shortLived = new JwtService("this-is-a-test-secret-key-that-is-long-enough-for-hmac-sha", 1);
        String token = shortLived.generateAccessToken("sai@example.com", "USER");

        Thread.sleep(10);

        assertThat(shortLived.isTokenValid(token, "sai@example.com")).isFalse();
    }
}
