package com.musicweb.support;

import com.musicweb.entity.UploadFile;
import com.musicweb.vo.UploadFileResponse;

public final class UploadFileResponseAssembler {

    private UploadFileResponseAssembler() {
    }

    public static UploadFileResponse toUploadFileResponse(UploadFile uploadFile) {
        return new UploadFileResponse(
                uploadFile.getId(),
                uploadFile.getFileType(),
                uploadFile.getOriginalName(),
                uploadFile.getUrl(),
                uploadFile.getMimeType(),
                uploadFile.getSizeBytes(),
                uploadFile.getCreatedAt()
        );
    }
}
