package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.common.ErrorCode;
import com.musicweb.dto.LoginRequest;
import com.musicweb.dto.RegisterRequest;
import com.musicweb.entity.User;
import com.musicweb.exception.BusinessException;
import com.musicweb.security.JwtService;
import com.musicweb.service.AdminAccountService;
import com.musicweb.service.AuthService;
import com.musicweb.service.UserService;
import com.musicweb.vo.AuthResponse;
import com.musicweb.vo.UserSummaryResponse;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String ROLE_USER = "USER";
    private static final int STATUS_ENABLED = 1;

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminAccountService adminAccountService;

    public AuthServiceImpl(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AdminAccountService adminAccountService
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.adminAccountService = adminAccountService;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        boolean exists = userService.count(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
        if (exists) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在", HttpStatus.CONFLICT);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(resolveNickname(request.nickname(), username));
        user.setRole(ROLE_USER);
        user.setStatus(STATUS_ENABLED);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userService.save(user);
        return toAuthResponse(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        User user = findExactUser(username);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (adminAccountService.isBootstrapAdminCredentials(username, request.password())) {
                return toAuthResponse(adminAccountService.ensureAdminAccount());
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "用户已被禁用", HttpStatus.FORBIDDEN);
        }
        return toAuthResponse(user);
    }

    private User findExactUser(String username) {
        return userService.list(new LambdaQueryWrapper<User>().eq(User::getUsername, username)).stream()
                .filter(user -> username.equals(user.getUsername()))
                .findFirst()
                .orElse(null);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), UserSummaryResponse.from(user));
    }

    private String resolveNickname(String nickname, String username) {
        if (StringUtils.hasText(nickname)) {
            return nickname.trim();
        }
        return username;
    }
}
