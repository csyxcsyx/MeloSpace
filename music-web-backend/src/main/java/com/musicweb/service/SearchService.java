package com.musicweb.service;

import com.musicweb.common.PageResult;
import com.musicweb.vo.AlbumResponse;
import com.musicweb.vo.ArtistResponse;
import com.musicweb.vo.PlaylistResponse;
import com.musicweb.vo.PublicUserResponse;
import com.musicweb.vo.SearchResponse;
import com.musicweb.vo.SearchSuggestionResponse;
import com.musicweb.vo.SongResponse;
import java.util.List;

public interface SearchService {

    SearchResponse search(String keyword);

    List<SearchSuggestionResponse> suggestions(String keyword, int limit);

    PageResult<SongResponse> searchSongs(String keyword, long page, long size);

    PageResult<ArtistResponse> searchArtists(String keyword, long page, long size);

    PageResult<AlbumResponse> searchAlbums(String keyword, long page, long size);

    PageResult<PlaylistResponse> searchPlaylists(String keyword, long page, long size);

    PageResult<PublicUserResponse> searchUsers(String keyword, long page, long size);
}
