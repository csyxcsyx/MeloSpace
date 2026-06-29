package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.common.PageResult;
import com.musicweb.dto.PlayRecordRequest;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.PlayHistoryService;
import com.musicweb.service.SongService;
import com.musicweb.vo.PlayHistoryResponse;
import com.musicweb.vo.SongResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final SongService songService;
    private final PlayHistoryService playHistoryService;

    public SongController(SongService songService, PlayHistoryService playHistoryService) {
        this.songService = songService;
        this.playHistoryService = playHistoryService;
    }

    @GetMapping
    public ApiResponse<PageResult<SongResponse>> listSongs(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long artistId,
            @RequestParam(required = false) Long albumId
    ) {
        return ApiResponse.ok(songService.listPublishedSongs(page, size, keyword, artistId, albumId));
    }

    @GetMapping("/{id}")
    public ApiResponse<SongResponse> getSong(@PathVariable @Positive Long id) {
        return ApiResponse.ok(songService.getPublishedSong(id));
    }

    @PostMapping("/{id}/play-record")
    public ApiResponse<PlayHistoryResponse> recordPlay(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PlayRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(playHistoryService.recordSongPlay(id, request, principal.getId()));
    }
}
