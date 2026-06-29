package com.musicweb.vo;

import java.time.LocalDateTime;

public record PlaylistSongResponse(
        Long id,
        Long songId,
        Integer sortOrder,
        SongResponse song,
        LocalDateTime createdAt
) {
}
