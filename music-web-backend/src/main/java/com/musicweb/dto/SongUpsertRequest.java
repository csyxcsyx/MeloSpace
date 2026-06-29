package com.musicweb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SongUpsertRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull @Positive Long artistId,
        @Positive Long albumId,
        @Size(max = 500) String coverUrl,
        @NotBlank @Size(max = 500) String audioUrl,
        @Size(max = 500) String lyricUrl,
        @Min(0) Integer durationSeconds,
        @Size(max = 20) String language,
        @Size(max = 50) String genre,
        @Size(max = 50) String mood,
        @Min(0) @Max(1) Integer status
) {
}
