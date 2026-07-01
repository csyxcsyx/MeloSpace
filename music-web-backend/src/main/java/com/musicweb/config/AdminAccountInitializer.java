package com.musicweb.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.entity.User;
import com.musicweb.service.UserService;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private static final String ADMIN_USERNAME = "YUXIANde";
    private static final String ADMIN_PASSWORD = "rex1234567";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final int STATUS_ENABLED = 1;

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public AdminAccountInitializer(
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
    public void run(ApplicationArguments args) {
        ensureCaseSensitiveUsernameColumn();
        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, ADMIN_USERNAME), false);
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
        } else {
            userService.updateById(user);
        }
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
