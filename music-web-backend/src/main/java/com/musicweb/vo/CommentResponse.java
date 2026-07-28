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
        LocalDateTime updatedAt,
        Long parentId,
        Long replyToUserId,
        String replyToNickname,
        String userNickname,
        String userAvatarUrl,
        int likeCount,
        int replyCount,
        boolean liked,
        boolean pinned,
        boolean deleted,
        boolean mine
) {
}
