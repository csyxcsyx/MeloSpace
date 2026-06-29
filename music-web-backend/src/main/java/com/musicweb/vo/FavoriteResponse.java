package com.musicweb.vo;

import java.time.LocalDateTime;

public record FavoriteResponse(
        Long id,
        Long userId,
        String targetType,
        Long targetId,
        LocalDateTime createdAt
) {
}
