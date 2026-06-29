package com.musicweb.vo;

public record AuthResponse(String token, UserSummaryResponse user) {
}
