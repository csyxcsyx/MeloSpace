package com.musicweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("song")
public class Song {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private Long artistId;
    private Long albumId;
    private String coverUrl;
    private String audioUrl;
    private String lyricUrl;
    private Integer durationSeconds;
    private String language;
    private String genre;
    private String mood;
    private Long playCount;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
