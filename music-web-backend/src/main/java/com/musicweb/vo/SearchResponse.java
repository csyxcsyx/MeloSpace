package com.musicweb.vo;

import java.util.List;

public record SearchResponse(
        List<SongResponse> songs,
        List<ArtistResponse> artists,
        List<AlbumResponse> albums,
        List<PlaylistResponse> playlists,
        List<PublicUserResponse> users,
        SearchTotalsResponse totals
) {

    public SearchResponse(
            List<SongResponse> songs,
            List<ArtistResponse> artists,
            List<AlbumResponse> albums,
            List<PlaylistResponse> playlists
    ) {
        this(songs, artists, albums, playlists, List.of(), SearchTotalsResponse.empty());
    }
}
