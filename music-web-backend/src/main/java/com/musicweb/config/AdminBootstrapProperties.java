package com.musicweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "music-web.admin-bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String username,
        String password,
        String nickname
) {
}
