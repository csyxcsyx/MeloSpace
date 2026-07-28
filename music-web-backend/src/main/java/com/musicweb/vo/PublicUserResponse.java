package com.musicweb.vo;

public record PublicUserResponse(
        Long id,
        String nickname,
        String avatarUrl,
        String bio,
        long publicPlaylistCount,
        long receivedFavoriteCount,
        long commentCount
) {
}
