package com.musicweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.common.ApiResponse;
import com.musicweb.common.ErrorCode;
import com.musicweb.entity.Artist;
import com.musicweb.exception.BusinessException;
import com.musicweb.service.ArtistService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.ArtistResponse;
import java.util.List;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @GetMapping
    public ApiResponse<List<ArtistResponse>> listArtists(@RequestParam(required = false) String keyword) {
        List<ArtistResponse> artists = artistService.list(new LambdaQueryWrapper<Artist>()
                        .like(StringUtils.hasText(keyword), Artist::getName, keyword)
                        .orderByDesc(Artist::getUpdatedAt)
                        .orderByDesc(Artist::getId))
                .stream()
                .map(MusicResponseAssembler::toArtistResponse)
                .toList();
        return ApiResponse.ok(artists);
    }

    @GetMapping("/{id}")
    public ApiResponse<ArtistResponse> getArtist(@PathVariable @Positive Long id) {
        Artist artist = artistService.getById(id);
        if (artist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌手不存在", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.ok(MusicResponseAssembler.toArtistResponse(artist));
    }
}
