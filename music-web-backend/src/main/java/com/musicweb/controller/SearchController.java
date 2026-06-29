package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.service.SongService;
import com.musicweb.vo.SearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SongService songService;

    public SearchController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping
    public ApiResponse<SearchResponse> search(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(songService.search(keyword));
    }
}
