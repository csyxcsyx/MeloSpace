package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.entity.User;
import com.musicweb.service.AdminAccountService;
import com.musicweb.service.UserService;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private static final String ADMIN_USERNAME = "YUXIANde";
    private static final String ADMIN_PASSWORD = "rex1234567";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int STATUS_ENABLED = 1;

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountServiceImpl(
            UserService userService,
            PasswordEncoder passwordEncoder
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean isBootstrapAdminCredentials(String username, String password) {
        return ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password);
    }

    @Override
    @Transactional
    public User ensureAdminAccount() {
        User user = findExactAdmin();
        if (user == null) {
            user = new User();
            user.setUsername(ADMIN_USERNAME);
            user.setNickname(ADMIN_USERNAME);
            user.setAvatarUrl(null);
            user.setCreatedAt(LocalDateTime.now());
        }

        user.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        user.setRole(ADMIN_ROLE);
        user.setStatus(STATUS_ENABLED);
        user.setUpdatedAt(LocalDateTime.now());

        if (user.getId() == null) {
            userService.save(user);
            return userService.getById(user.getId());
        }
        userService.updateById(user);
        return userService.getById(user.getId());
    }

    private User findExactAdmin() {
        return userService.list(new LambdaQueryWrapper<User>().eq(User::getUsername, ADMIN_USERNAME)).stream()
                .filter(user -> ADMIN_USERNAME.equals(user.getUsername()))
                .findFirst()
                .orElse(null);
    }
}
