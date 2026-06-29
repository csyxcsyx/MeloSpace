package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.security.UserPrincipal;
import com.musicweb.vo.UserSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public ApiResponse<UserSummaryResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(new UserSummaryResponse(
                principal.getId(),
                principal.getUsername(),
                principal.getNickname(),
                principal.getAvatarUrl(),
                principal.getRole()
        ));
    }
}
