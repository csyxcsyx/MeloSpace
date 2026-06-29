package com.musicweb.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record PlayRecordRequest(
        @Min(0) Integer progressSeconds,
        @Size(max = 30) String sourceType
) {
}
