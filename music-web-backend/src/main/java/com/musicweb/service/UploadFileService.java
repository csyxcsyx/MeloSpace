package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.entity.UploadFile;
import com.musicweb.vo.UploadFileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UploadFileService extends IService<UploadFile> {

    UploadFileResponse upload(MultipartFile file, String fileType, Long ownerId);
}
