package com.musicweb.vo;

public record SearchSuggestionResponse(
        String type,
        Long id,
        String title,
        String subtitle,
        String imageUrl
) {
}
