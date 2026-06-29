package com.musicweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("playlist")
public class Playlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String description;
    private String coverUrl;
    private String visibility;
    private Long playCount;
    private Long favoriteCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
