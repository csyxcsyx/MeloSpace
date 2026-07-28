package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCommentActionRequest(
        @NotBlank @Size(max = 20) String action
) {
}
