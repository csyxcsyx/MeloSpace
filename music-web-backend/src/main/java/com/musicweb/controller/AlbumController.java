package com.musicweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.common.ApiResponse;
import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.AlbumResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final AlbumService albumService;
    private final ArtistService artistService;

    public AlbumController(AlbumService albumService, ArtistService artistService) {
        this.albumService = albumService;
        this.artistService = artistService;
    }

    @GetMapping
    public ApiResponse<List<AlbumResponse>> listAlbums(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long artistId
    ) {
        List<Album> albums = albumService.list(new LambdaQueryWrapper<Album>()
                .like(StringUtils.hasText(keyword), Album::getTitle, keyword)
                .eq(artistId != null, Album::getArtistId, artistId)
                .orderByDesc(Album::getUpdatedAt)
                .orderByDesc(Album::getId));
        Map<Long, Artist> artistsById = albums.isEmpty()
                ? Collections.emptyMap()
                : artistService.listByIds(albums.stream().map(Album::getArtistId).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Artist::getId, Function.identity()));
        return ApiResponse.ok(albums.stream()
                .map(album -> MusicResponseAssembler.toAlbumResponse(album, artistsById))
                .toList());
    }
}
