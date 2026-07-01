package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.exception.BusinessException;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.UserAccountService;
import com.musicweb.vo.AdminUserResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserAccountService userAccountService;

    public AdminUserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminUserResponse>> listUsers(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status
    ) {
        return ApiResponse.ok(userAccountService.listUsers(page, size, keyword, role, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal.getId().equals(id)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请在个人页注销当前登录账号", HttpStatus.BAD_REQUEST);
        }
        userAccountService.deleteUser(id);
        return ApiResponse.ok();
    }
}
