package com.musicweb.vo;

import java.time.LocalDateTime;

public record PlayHistoryResponse(
        Long id,
        Long userId,
        Long songId,
        Integer progressSeconds,
        String sourceType,
        LocalDateTime playedAt,
        SongResponse song
) {
}
