package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.common.PageResult;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.FavoriteService;
import com.musicweb.service.PlayHistoryService;
import com.musicweb.service.PlaylistService;
import com.musicweb.vo.FavoriteResponse;
import com.musicweb.vo.PlayHistoryResponse;
import com.musicweb.vo.PlaylistResponse;
import com.musicweb.vo.UserSummaryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final PlaylistService playlistService;
    private final FavoriteService favoriteService;
    private final PlayHistoryService playHistoryService;

    public UserController(
            PlaylistService playlistService,
            FavoriteService favoriteService,
            PlayHistoryService playHistoryService
    ) {
        this.playlistService = playlistService;
        this.favoriteService = favoriteService;
        this.playHistoryService = playHistoryService;
    }

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

    @GetMapping("/me/playlists")
    public ApiResponse<PageResult<PlaylistResponse>> myPlaylists(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size
    ) {
        return ApiResponse.ok(playlistService.listUserPlaylists(principal.getId(), page, size));
    }

    @GetMapping("/me/favorites")
    public ApiResponse<PageResult<FavoriteResponse>> myFavorites(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size
    ) {
        return ApiResponse.ok(favoriteService.listUserFavorites(principal.getId(), page, size));
    }

    @GetMapping("/me/recent-plays")
    public ApiResponse<PageResult<PlayHistoryResponse>> recentPlays(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size
    ) {
        return ApiResponse.ok(playHistoryService.listRecentPlays(principal.getId(), page, size));
    }
}
