package com.musicweb.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaylistSongBatchRequest(
        @NotEmpty @Size(max = 200) List<@Positive Long> songIds
) {
}
