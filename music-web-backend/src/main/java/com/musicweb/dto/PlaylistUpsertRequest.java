package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaylistUpsertRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 500) String description,
        @Size(max = 500) String coverUrl,
        @Size(max = 20) String visibility,
        @Size(max = 5) List<@NotBlank @Size(max = 12) String> tags
) {
}
