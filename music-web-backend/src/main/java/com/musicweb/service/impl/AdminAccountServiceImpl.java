package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.config.AdminBootstrapProperties;
import com.musicweb.entity.User;
import com.musicweb.service.AdminAccountService;
import com.musicweb.service.UserService;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final int STATUS_ENABLED = 1;
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    public AdminAccountServiceImpl(
            UserService userService,
            PasswordEncoder passwordEncoder,
            AdminBootstrapProperties properties
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public User ensureAdminAccount() {
        if (!properties.enabled()) {
            return null;
        }

        String username = requireUsername();
        String password = requirePassword();
        User existingUser = findExactUser(username);
        if (existingUser != null) {
            if (!ADMIN_ROLE.equals(existingUser.getRole())) {
                throw new IllegalStateException(
                        "Admin bootstrap username already belongs to a non-admin account"
                );
            }
            return existingUser;
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(resolveNickname(username));
        user.setAvatarUrl(null);
        user.setRole(ADMIN_ROLE);
        user.setStatus(STATUS_ENABLED);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        if (!userService.save(user)) {
            throw new IllegalStateException("Failed to create the configured bootstrap admin account");
        }
        return user;
    }

    private User findExactUser(String username) {
        return userService.list(new LambdaQueryWrapper<User>().eq(User::getUsername, username)).stream()
                .filter(user -> username.equals(user.getUsername()))
                .findFirst()
                .orElse(null);
    }

    private String requireUsername() {
        if (!StringUtils.hasText(properties.username())) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_USERNAME is required when admin bootstrap is enabled"
            );
        }
        String username = properties.username().trim();
        if (!username.matches("^[A-Za-z]{3,50}$")) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_USERNAME must contain 3-50 English letters"
            );
        }
        return username;
    }

    private String requirePassword() {
        if (!StringUtils.hasText(properties.password())
                || properties.password().length() < MINIMUM_PASSWORD_LENGTH
                || properties.password().length() > 64) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_PASSWORD must contain 12-64 characters"
            );
        }
        return properties.password();
    }

    private String resolveNickname(String username) {
        if (!StringUtils.hasText(properties.nickname())) {
            return username;
        }
        String nickname = properties.nickname().trim();
        if (nickname.length() > 50) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_NICKNAME must contain at most 50 characters"
            );
        }
        return nickname;
    }
}
