package com.musicweb.vo;

import com.musicweb.entity.User;

public record UserSummaryResponse(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        String role
) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole()
        );
    }
}
