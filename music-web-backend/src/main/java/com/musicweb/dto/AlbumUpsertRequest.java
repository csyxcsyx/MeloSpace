package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AlbumUpsertRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull @Positive Long artistId,
        @Size(max = 500) String coverUrl,
        LocalDate releaseDate
) {
}
