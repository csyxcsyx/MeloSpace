package com.musicweb.vo;

public record SearchTotalsResponse(
        long songs,
        long artists,
        long albums,
        long playlists,
        long users
) {

    public static SearchTotalsResponse empty() {
        return new SearchTotalsResponse(0, 0, 0, 0, 0);
    }
}
