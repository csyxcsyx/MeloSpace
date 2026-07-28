package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.UploadFileService;
import com.musicweb.vo.UploadFileResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadFileService uploadFileService;

    public UploadController(UploadFileService uploadFileService) {
        this.uploadFileService = uploadFileService;
    }

    @PostMapping("/images")
    public ApiResponse<UploadFileResponse> uploadImage(
            @RequestParam String purpose,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(uploadFileService.uploadImage(file, purpose, principal.getId()));
    }
}
