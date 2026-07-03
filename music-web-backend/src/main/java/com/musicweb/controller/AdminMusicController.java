package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.common.PageResult;
import com.musicweb.dto.AlbumUpsertRequest;
import com.musicweb.dto.ArtistUpsertRequest;
import com.musicweb.dto.LddcLyricRequest;
import com.musicweb.dto.SongStatusRequest;
import com.musicweb.dto.SongUpsertRequest;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import com.musicweb.service.LddcLyricService;
import com.musicweb.service.SongService;
import com.musicweb.service.UploadFileService;
import com.musicweb.vo.AlbumResponse;
import com.musicweb.vo.ArtistResponse;
import com.musicweb.vo.LddcLyricResponse;
import com.musicweb.vo.SongResponse;
import com.musicweb.vo.UploadFileResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminMusicController {

    private final SongService songService;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final UploadFileService uploadFileService;
    private final LddcLyricService lddcLyricService;

    public AdminMusicController(
            SongService songService,
            ArtistService artistService,
            AlbumService albumService,
            UploadFileService uploadFileService,
            LddcLyricService lddcLyricService
    ) {
        this.songService = songService;
        this.artistService = artistService;
        this.albumService = albumService;
        this.uploadFileService = uploadFileService;
        this.lddcLyricService = lddcLyricService;
    }

    @GetMapping("/songs")
    public ApiResponse<PageResult<SongResponse>> listSongs(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @Min(0) @Max(1) Integer status
    ) {
        return ApiResponse.ok(songService.listAdminSongs(page, size, keyword, status));
    }

    @PostMapping("/songs")
    public ApiResponse<SongResponse> createSong(@Valid @RequestBody SongUpsertRequest request) {
        return ApiResponse.ok(songService.createSong(request));
    }

    @PutMapping("/songs/{id}")
    public ApiResponse<SongResponse> updateSong(
            @PathVariable @Positive Long id,
            @Valid @RequestBody SongUpsertRequest request
    ) {
        return ApiResponse.ok(songService.updateSong(id, request));
    }

    @PatchMapping("/songs/{id}/status")
    public ApiResponse<SongResponse> updateSongStatus(
            @PathVariable @Positive Long id,
            @Valid @RequestBody SongStatusRequest request
    ) {
        return ApiResponse.ok(songService.updateSongStatus(id, request));
    }

    @DeleteMapping("/songs/{id}")
    public ApiResponse<Void> deleteSong(@PathVariable @Positive Long id) {
        songService.deleteSong(id);
        return ApiResponse.ok();
    }

    @PostMapping("/artists")
    public ApiResponse<ArtistResponse> createArtist(@Valid @RequestBody ArtistUpsertRequest request) {
        return ApiResponse.ok(artistService.createArtist(request));
    }

    @PutMapping("/artists/{id}")
    public ApiResponse<ArtistResponse> updateArtist(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ArtistUpsertRequest request
    ) {
        return ApiResponse.ok(artistService.updateArtist(id, request));
    }

    @DeleteMapping("/artists/{id}")
    public ApiResponse<Void> deleteArtist(@PathVariable @Positive Long id) {
        artistService.deleteArtist(id);
        return ApiResponse.ok();
    }

    @PostMapping("/albums")
    public ApiResponse<AlbumResponse> createAlbum(@Valid @RequestBody AlbumUpsertRequest request) {
        return ApiResponse.ok(albumService.createAlbum(request));
    }

    @PutMapping("/albums/{id}")
    public ApiResponse<AlbumResponse> updateAlbum(
            @PathVariable @Positive Long id,
            @Valid @RequestBody AlbumUpsertRequest request
    ) {
        return ApiResponse.ok(albumService.updateAlbum(id, request));
    }

    @DeleteMapping("/albums/{id}")
    public ApiResponse<Void> deleteAlbum(@PathVariable @Positive Long id) {
        albumService.deleteAlbum(id);
        return ApiResponse.ok();
    }

    @PostMapping("/lyrics/lddc")
    public ApiResponse<LddcLyricResponse> importLddcLyrics(@Valid @RequestBody LddcLyricRequest request) {
        return ApiResponse.ok(lddcLyricService.importLyrics(request));
    }

    @PostMapping("/upload")
    public ApiResponse<UploadFileResponse> upload(
            @RequestParam MultipartFile file,
            @RequestParam String fileType,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(uploadFileService.upload(file, fileType, principal.getId()));
    }
}
