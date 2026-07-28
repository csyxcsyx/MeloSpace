package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.common.PageResult;
import com.musicweb.dto.AdminCommentActionRequest;
import com.musicweb.service.CommentService;
import com.musicweb.vo.CommentReportResponse;
import com.musicweb.vo.CommentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminCommentController {

    private final CommentService commentService;

    public AdminCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/comment-reports")
    public ApiResponse<PageResult<CommentReportResponse>> listReports(
            @RequestParam(defaultValue = "OPEN") @Size(max = 20) String status,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size
    ) {
        return ApiResponse.ok(commentService.listReports(status, page, size));
    }

    @PatchMapping("/comments/{id}")
    public ApiResponse<CommentResponse> moderateComment(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AdminCommentActionRequest request
    ) {
        return ApiResponse.ok(commentService.moderateComment(id, request));
    }
}
