package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank @Size(max = 50) String nickname,
        @Size(max = 500) String avatarUrl,
        @Size(max = 500) String bio
) {
}
