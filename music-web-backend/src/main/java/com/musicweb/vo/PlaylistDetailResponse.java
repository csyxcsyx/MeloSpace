package com.musicweb.vo;

import java.time.LocalDateTime;
import java.util.List;

public record PlaylistDetailResponse(
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
        LocalDateTime updatedAt,
        String creatorNickname,
        String creatorAvatarUrl,
        List<String> tags,
        Long commentCount,
        boolean favorited,
        boolean canManage,
        List<PlaylistSongResponse> songs
) {
}
