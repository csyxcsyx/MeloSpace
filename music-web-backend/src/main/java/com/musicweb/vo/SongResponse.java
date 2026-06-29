package com.musicweb.vo;

import java.time.LocalDateTime;

public record SongResponse(
        Long id,
        String title,
        Long artistId,
        String artistName,
        Long albumId,
        String albumTitle,
        String coverUrl,
        String audioUrl,
        String lyricUrl,
        Integer durationSeconds,
        String language,
        String genre,
        String mood,
        Long playCount,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
