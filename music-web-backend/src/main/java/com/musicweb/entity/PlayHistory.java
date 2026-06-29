package com.musicweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("play_history")
public class PlayHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long songId;
    private Integer progressSeconds;
    private String sourceType;
    private LocalDateTime playedAt;
}
