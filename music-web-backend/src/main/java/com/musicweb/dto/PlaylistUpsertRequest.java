package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaylistUpsertRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 500) String description,
        @Size(max = 500) String coverUrl,
        @Size(max = 20) String visibility
) {
}
