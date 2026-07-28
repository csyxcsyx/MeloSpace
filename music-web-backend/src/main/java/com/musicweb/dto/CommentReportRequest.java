package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentReportRequest(
        @NotBlank @Size(max = 30) String reason,
        @Size(max = 500) String detail
) {
}
