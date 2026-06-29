package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank @Size(max = 20) String targetType,
        @NotNull @Positive Long targetId,
        @NotBlank @Size(max = 1000) String content
) {
}
