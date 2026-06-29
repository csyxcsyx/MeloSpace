package com.musicweb.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AlbumResponse(
        Long id,
        String title,
        Long artistId,
        String artistName,
        String coverUrl,
        LocalDate releaseDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
