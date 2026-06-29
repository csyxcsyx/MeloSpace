package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.common.PageResult;
import com.musicweb.dto.CommentRequest;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.CommentService;
import com.musicweb.vo.CommentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ApiResponse<PageResult<CommentResponse>> listComments(
            @RequestParam @NotBlank @Size(max = 20) String targetType,
            @RequestParam @Positive Long targetId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size
    ) {
        return ApiResponse.ok(commentService.listComments(targetType, targetId, page, size));
    }

    @PostMapping
    public ApiResponse<CommentResponse> createComment(
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(commentService.createComment(request, principal.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteComment(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        commentService.deleteComment(id, principal.getId());
        return ApiResponse.ok();
    }
}
