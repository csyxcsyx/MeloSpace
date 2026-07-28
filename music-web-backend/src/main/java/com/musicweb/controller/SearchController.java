package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.common.PageResult;
import com.musicweb.service.SearchService;
import com.musicweb.vo.AlbumResponse;
import com.musicweb.vo.ArtistResponse;
import com.musicweb.vo.PlaylistResponse;
import com.musicweb.vo.PublicUserResponse;
import com.musicweb.vo.SearchResponse;
import com.musicweb.vo.SearchSuggestionResponse;
import com.musicweb.vo.SongResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ApiResponse<SearchResponse> search(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(searchService.search(keyword));
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<SearchSuggestionResponse>> suggestions(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.ok(searchService.suggestions(keyword, limit));
    }

    @GetMapping("/songs")
    public ApiResponse<PageResult<SongResponse>> searchSongs(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(searchService.searchSongs(keyword, page, size));
    }

    @GetMapping("/artists")
    public ApiResponse<PageResult<ArtistResponse>> searchArtists(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(searchService.searchArtists(keyword, page, size));
    }

    @GetMapping("/albums")
    public ApiResponse<PageResult<AlbumResponse>> searchAlbums(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(searchService.searchAlbums(keyword, page, size));
    }

    @GetMapping("/playlists")
    public ApiResponse<PageResult<PlaylistResponse>> searchPlaylists(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(searchService.searchPlaylists(keyword, page, size));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<PublicUserResponse>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(searchService.searchUsers(keyword, page, size));
    }
}
