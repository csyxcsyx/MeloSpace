package com.musicweb.mapper.projection;

import lombok.Data;

@Data
public class SearchUserProjection {

    private Long id;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private Long publicPlaylistCount;
    private Long receivedFavoriteCount;
    private Long commentCount;
}
