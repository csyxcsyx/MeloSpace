package com.musicweb.vo;

import java.time.LocalDateTime;

public record UploadFileResponse(
        Long id,
        String fileType,
        String originalName,
        String url,
        String mimeType,
        Long sizeBytes,
        LocalDateTime createdAt
) {
}
