package com.musicweb.vo;

import com.musicweb.entity.User;
import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        String role,
        Integer status,
        String passwordState,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                "已加密存储，不显示明文",
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
