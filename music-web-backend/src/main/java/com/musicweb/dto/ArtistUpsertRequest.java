package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArtistUpsertRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String bio,
        @Size(max = 500) String avatarUrl
) {
}
