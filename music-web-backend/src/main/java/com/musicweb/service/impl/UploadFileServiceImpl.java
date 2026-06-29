package com.musicweb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.entity.UploadFile;
import com.musicweb.mapper.UploadFileMapper;
import com.musicweb.service.UploadFileService;
import org.springframework.stereotype.Service;

@Service
public class UploadFileServiceImpl extends ServiceImpl<UploadFileMapper, UploadFile> implements UploadFileService {
}
