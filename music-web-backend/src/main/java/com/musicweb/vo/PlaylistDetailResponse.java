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
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PlaylistSongResponse> songs
) {
}
