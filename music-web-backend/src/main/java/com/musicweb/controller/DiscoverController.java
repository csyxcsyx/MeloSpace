package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.DiscoverService;
import com.musicweb.vo.CommunityDiscoverResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discover")
public class DiscoverController {

    private final DiscoverService discoverService;

    public DiscoverController(DiscoverService discoverService) {
        this.discoverService = discoverService;
    }

    @GetMapping("/community")
    public ApiResponse<CommunityDiscoverResponse> community(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(discoverService.community(principal == null ? null : principal.getId()));
    }
}
