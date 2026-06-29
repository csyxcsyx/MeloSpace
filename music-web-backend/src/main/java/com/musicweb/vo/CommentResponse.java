package com.musicweb.vo;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long userId,
        String targetType,
        Long targetId,
        String content,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
