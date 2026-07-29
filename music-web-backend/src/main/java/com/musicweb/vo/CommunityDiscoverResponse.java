package com.musicweb.vo;

import java.util.List;

public record CommunityDiscoverResponse(
        List<PlaylistResponse> popularPlaylists,
        List<PlaylistResponse> latestPlaylists,
        List<DiscoverCommentResponse> hotComments
) {
}
