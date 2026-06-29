package com.musicweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("upload_file")
public class UploadFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;
    private String fileType;
    private String originalName;
    private String storagePath;
    private String url;
    private String mimeType;
    private Long sizeBytes;
    private LocalDateTime createdAt;
}
