package com.musicweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("comment_report")
public class CommentReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long commentId;
    private Long userId;
    private String reason;
    private String detail;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
