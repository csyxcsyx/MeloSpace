package com.musicweb.vo;

import java.time.LocalDateTime;

public record DiscoverCommentResponse(
        Long id,
        Long userId,
        String userNickname,
        String userAvatarUrl,
        String targetType,
        Long targetId,
        String targetTitle,
        String targetCoverUrl,
        String content,
        int likeCount,
        int replyCount,
        LocalDateTime createdAt
) {
}
