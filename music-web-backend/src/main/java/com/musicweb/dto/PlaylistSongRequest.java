package com.musicweb.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PlaylistSongRequest(
        @NotNull @Positive Long songId
) {
}
