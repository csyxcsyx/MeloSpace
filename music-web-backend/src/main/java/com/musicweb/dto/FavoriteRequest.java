package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FavoriteRequest(
        @NotBlank @Size(max = 20) String targetType,
        @NotNull @Positive Long targetId
) {
}
