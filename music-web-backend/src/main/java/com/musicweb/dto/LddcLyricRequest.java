package com.musicweb.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LddcLyricRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 100) String artist,
        @Size(max = 100) String album,
        @NotBlank @Size(max = 500) String audioUrl,
        @Min(0) Integer durationSeconds
) {
}
