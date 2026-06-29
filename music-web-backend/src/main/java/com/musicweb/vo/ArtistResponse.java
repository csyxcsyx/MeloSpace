package com.musicweb.vo;

import java.time.LocalDateTime;

public record ArtistResponse(
        Long id,
        String name,
        String bio,
        String avatarUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
