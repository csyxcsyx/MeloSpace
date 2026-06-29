package com.musicweb.vo;

import java.time.LocalDateTime;

public record PlaylistResponse(
        Long id,
        Long userId,
        String title,
        String description,
        String coverUrl,
        String visibility,
        Long playCount,
        Long favoriteCount,
        Long songCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
