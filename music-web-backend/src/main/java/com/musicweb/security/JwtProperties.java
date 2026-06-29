package com.musicweb.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "music-web.jwt")
public record JwtProperties(String secret, String issuer, long expirationMinutes) {
}
