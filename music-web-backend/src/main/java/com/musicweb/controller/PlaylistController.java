package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.common.PageResult;
import com.musicweb.dto.PlaylistOrderRequest;
import com.musicweb.dto.PlaylistSongRequest;
import com.musicweb.dto.PlaylistUpsertRequest;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.PlaylistService;
import com.musicweb.vo.PlaylistDetailResponse;
import com.musicweb.vo.PlaylistResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @GetMapping
    public ApiResponse<PageResult<PlaylistResponse>> listPlaylists(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(playlistService.listPublicPlaylists(page, size, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlaylistDetailResponse> getPlaylist(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long currentUserId = principal == null ? null : principal.getId();
        return ApiResponse.ok(playlistService.getPlaylist(id, currentUserId));
    }

    @PostMapping
    public ApiResponse<PlaylistDetailResponse> createPlaylist(
            @Valid @RequestBody PlaylistUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(playlistService.createPlaylist(request, principal.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlaylistDetailResponse> updatePlaylist(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PlaylistUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(playlistService.updatePlaylist(id, request, principal.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlaylist(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        playlistService.deletePlaylist(id, principal.getId());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/songs")
    public ApiResponse<PlaylistDetailResponse> addSong(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PlaylistSongRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(playlistService.addSong(id, request, principal.getId()));
    }

    @DeleteMapping("/{id}/songs/{songId}")
    public ApiResponse<PlaylistDetailResponse> removeSong(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Long songId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(playlistService.removeSong(id, songId, principal.getId()));
    }

    @PutMapping("/{id}/songs/order")
    public ApiResponse<PlaylistDetailResponse> reorderSongs(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PlaylistOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(playlistService.reorderSongs(id, request, principal.getId()));
    }
}
