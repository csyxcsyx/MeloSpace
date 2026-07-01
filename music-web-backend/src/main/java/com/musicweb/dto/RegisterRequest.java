package com.musicweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z]+$", message = "账号名只能包含英文字母")
        String username,

        @NotBlank
        @Size(min = 8, max = 64)
        String password,

        @Size(max = 50)
        String nickname
) {
}
