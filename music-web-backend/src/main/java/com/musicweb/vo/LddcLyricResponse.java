package com.musicweb.vo;

public record LddcLyricResponse(
        String lyricUrl,
        String outputPath,
        String source,
        String matchedTitle,
        String matchedArtist,
        String matchedAlbum,
        Integer durationMs,
        String format
) {
}
