package com.musicweb.vo;

import java.util.List;

public record SearchResponse(
        List<SongResponse> songs,
        List<ArtistResponse> artists,
        List<AlbumResponse> albums
) {
}
