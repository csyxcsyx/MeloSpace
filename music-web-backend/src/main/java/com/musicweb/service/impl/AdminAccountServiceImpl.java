package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.entity.User;
import com.musicweb.service.AdminAccountService;
import com.musicweb.service.UserService;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountServiceImpl.class);
    private static final String ADMIN_USERNAME = "YUXIANde";
    private static final String ADMIN_PASSWORD = "rex1234567";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int STATUS_ENABLED = 1;

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public AdminAccountServiceImpl(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate,
            DataSource dataSource
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public boolean isBootstrapAdminCredentials(String username, String password) {
        return ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password);
    }

    @Override
    @Transactional
    public User ensureAdminAccount() {
        ensureCaseSensitiveUsernameColumn();
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

    private void ensureCaseSensitiveUsernameColumn() {
        if (!isMySql()) {
            return;
        }
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE `user`
                      MODIFY username VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL
                    """);
        } catch (RuntimeException exception) {
            log.warn("Could not update user.username collation to case-sensitive mode", exception);
        }
    }

    private boolean isMySql() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (SQLException exception) {
            log.warn("Could not inspect database product name", exception);
            return false;
        }
    }
}
