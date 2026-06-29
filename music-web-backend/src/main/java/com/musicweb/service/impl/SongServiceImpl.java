package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.Song;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.SongMapper;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import com.musicweb.service.SongService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.AlbumResponse;
import com.musicweb.vo.ArtistResponse;
import com.musicweb.vo.SearchResponse;
import com.musicweb.vo.SongResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SongServiceImpl extends ServiceImpl<SongMapper, Song> implements SongService {

    private static final int STATUS_PUBLISHED = 1;
    private static final int SEARCH_LIMIT = 10;

    private final ArtistService artistService;
    private final AlbumService albumService;

    public SongServiceImpl(ArtistService artistService, AlbumService albumService) {
        this.artistService = artistService;
        this.albumService = albumService;
    }

    @Override
    public PageResult<SongResponse> listPublishedSongs(
            long page,
            long size,
            String keyword,
            Long artistId,
            Long albumId
    ) {
        LambdaQueryWrapper<Song> wrapper = basePublishedWrapper()
                .eq(artistId != null, Song::getArtistId, artistId)
                .eq(albumId != null, Song::getAlbumId, albumId)
                .like(StringUtils.hasText(keyword), Song::getTitle, keyword)
                .orderByDesc(Song::getUpdatedAt)
                .orderByDesc(Song::getId);
        Page<Song> songPage = page(new Page<>(page, size), wrapper);
        return toSongPageResult(songPage);
    }

    @Override
    public SongResponse getPublishedSong(Long id) {
        Song song = getById(id);
        if (song == null || !Objects.equals(song.getStatus(), STATUS_PUBLISHED)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌曲不存在或已下架", HttpStatus.NOT_FOUND);
        }
        return toSongResponses(List.of(song)).get(0);
    }

    @Override
    public SearchResponse search(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new SearchResponse(List.of(), List.of(), List.of());
        }

        Page<Song> songPage = page(
                new Page<>(1, SEARCH_LIMIT),
                basePublishedWrapper()
                        .like(Song::getTitle, keyword)
                        .orderByDesc(Song::getPlayCount)
                        .orderByDesc(Song::getId)
        );
        List<Artist> artists = artistService.page(
                new Page<>(1, SEARCH_LIMIT),
                new LambdaQueryWrapper<Artist>()
                        .like(Artist::getName, keyword)
                        .orderByDesc(Artist::getUpdatedAt)
                        .orderByDesc(Artist::getId)
        ).getRecords();
        List<Album> albums = albumService.page(
                new Page<>(1, SEARCH_LIMIT),
                new LambdaQueryWrapper<Album>()
                        .like(Album::getTitle, keyword)
                        .orderByDesc(Album::getUpdatedAt)
                        .orderByDesc(Album::getId)
        ).getRecords();

        Map<Long, Artist> albumArtists = loadArtistsByIds(
                albums.stream().map(Album::getArtistId).collect(Collectors.toSet())
        );
        return new SearchResponse(
                toSongResponses(songPage.getRecords()),
                artists.stream().map(MusicResponseAssembler::toArtistResponse).toList(),
                albums.stream().map(album -> MusicResponseAssembler.toAlbumResponse(album, albumArtists)).toList()
        );
    }

    private LambdaQueryWrapper<Song> basePublishedWrapper() {
        return new LambdaQueryWrapper<Song>().eq(Song::getStatus, STATUS_PUBLISHED);
    }

    private PageResult<SongResponse> toSongPageResult(Page<Song> page) {
        return new PageResult<>(
                toSongResponses(page.getRecords()),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
    }

    private List<SongResponse> toSongResponses(List<Song> songs) {
        if (songs.isEmpty()) {
            return List.of();
        }
        Map<Long, Artist> artistsById = loadArtistsByIds(
                songs.stream().map(Song::getArtistId).collect(Collectors.toSet())
        );
        Map<Long, Album> albumsById = loadAlbumsByIds(
                songs.stream().map(Song::getAlbumId).filter(Objects::nonNull).collect(Collectors.toSet())
        );
        return songs.stream()
                .map(song -> MusicResponseAssembler.toSongResponse(song, artistsById, albumsById))
                .toList();
    }

    private Map<Long, Artist> loadArtistsByIds(Set<Long> artistIds) {
        if (artistIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return artistService.listByIds(artistIds).stream()
                .collect(Collectors.toMap(Artist::getId, Function.identity()));
    }

    private Map<Long, Album> loadAlbumsByIds(Set<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return albumService.listByIds(albumIds).stream()
                .collect(Collectors.toMap(Album::getId, Function.identity()));
    }
}
