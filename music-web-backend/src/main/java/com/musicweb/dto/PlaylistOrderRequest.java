package com.musicweb.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record PlaylistOrderRequest(
        @NotEmpty List<@NotNull @Positive Long> songIds
) {
}
