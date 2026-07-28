package com.musicweb.vo;

import java.time.LocalDateTime;

public record CommentReportResponse(
        Long id,
        Long commentId,
        Long reporterUserId,
        String reporterNickname,
        String reason,
        String detail,
        String status,
        String commentContent,
        Integer commentStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
