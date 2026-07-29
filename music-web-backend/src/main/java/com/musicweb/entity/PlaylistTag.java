package com.musicweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("playlist_tag")
public class PlaylistTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playlistId;
    private String tag;
    private Integer sortOrder;
}
