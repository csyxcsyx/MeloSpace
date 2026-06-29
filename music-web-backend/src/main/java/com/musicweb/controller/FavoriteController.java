package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.dto.FavoriteRequest;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.FavoriteService;
import com.musicweb.vo.FavoriteResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping
    public ApiResponse<FavoriteResponse> favorite(
            @Valid @RequestBody FavoriteRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(favoriteService.favorite(request, principal.getId()));
    }

    @DeleteMapping
    public ApiResponse<Void> unfavorite(
            @RequestParam @NotBlank @Size(max = 20) String targetType,
            @RequestParam @Positive Long targetId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        favoriteService.unfavorite(targetType, targetId, principal.getId());
        return ApiResponse.ok();
    }
}
