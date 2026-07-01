package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.dto.SongStatusRequest;
import com.musicweb.dto.SongUpsertRequest;
import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.Comment;
import com.musicweb.entity.Favorite;
import com.musicweb.entity.PlayHistory;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.PlaylistSong;
import com.musicweb.entity.Song;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.CommentMapper;
import com.musicweb.mapper.FavoriteMapper;
import com.musicweb.mapper.PlayHistoryMapper;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.mapper.SongMapper;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import com.musicweb.service.PlaylistSongService;
import com.musicweb.service.SongService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.AlbumResponse;
import com.musicweb.vo.ArtistResponse;
import com.musicweb.vo.PlaylistResponse;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SongServiceImpl extends ServiceImpl<SongMapper, Song> implements SongService {

    private static final int STATUS_PUBLISHED = 1;
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String TARGET_TYPE_SONG = "SONG";
    private static final int SEARCH_LIMIT = 10;

    private final ArtistService artistService;
    private final AlbumService albumService;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongService playlistSongService;
    private final FavoriteMapper favoriteMapper;
    private final CommentMapper commentMapper;
    private final PlayHistoryMapper playHistoryMapper;

    public SongServiceImpl(
            ArtistService artistService,
            AlbumService albumService,
            PlaylistMapper playlistMapper,
            PlaylistSongService playlistSongService,
            FavoriteMapper favoriteMapper,
            CommentMapper commentMapper,
            PlayHistoryMapper playHistoryMapper
    ) {
        this.artistService = artistService;
        this.albumService = albumService;
        this.playlistMapper = playlistMapper;
        this.playlistSongService = playlistSongService;
        this.favoriteMapper = favoriteMapper;
        this.commentMapper = commentMapper;
        this.playHistoryMapper = playHistoryMapper;
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
            return new SearchResponse(List.of(), List.of(), List.of(), List.of());
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
        List<Playlist> playlists = playlistMapper.selectPage(
                new Page<>(1, SEARCH_LIMIT),
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getVisibility, VISIBILITY_PUBLIC)
                        .like(Playlist::getTitle, keyword)
                        .orderByDesc(Playlist::getUpdatedAt)
                        .orderByDesc(Playlist::getId)
        ).getRecords();

        Map<Long, Artist> albumArtists = loadArtistsByIds(
                albums.stream().map(Album::getArtistId).collect(Collectors.toSet())
        );
        return new SearchResponse(
                toSongResponses(songPage.getRecords()),
                artists.stream().map(MusicResponseAssembler::toArtistResponse).toList(),
                albums.stream().map(album -> MusicResponseAssembler.toAlbumResponse(album, albumArtists)).toList(),
                playlists.stream().map(this::toPlaylistResponse).toList()
        );
    }

    @Override
    public PageResult<SongResponse> listAdminSongs(long page, long size, String keyword, Integer status) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<Song>()
                .eq(status != null, Song::getStatus, status)
                .like(StringUtils.hasText(keyword), Song::getTitle, keyword)
                .orderByDesc(Song::getUpdatedAt)
                .orderByDesc(Song::getId);
        Page<Song> songPage = page(new Page<>(page, size), wrapper);
        return toSongPageResult(songPage);
    }

    @Override
    public SongResponse createSong(SongUpsertRequest request) {
        validateSongRelations(request.artistId(), request.albumId());
        Song song = new Song();
        applySongRequest(song, request);
        song.setPlayCount(0L);
        song.setStatus(request.status() == null ? STATUS_PUBLISHED : request.status());
        save(song);
        return toSongResponses(List.of(getById(song.getId()))).get(0);
    }

    @Override
    public SongResponse updateSong(Long id, SongUpsertRequest request) {
        Song song = getExistingSong(id);
        validateSongRelations(request.artistId(), request.albumId());
        applySongRequest(song, request);
        song.setStatus(request.status() == null ? song.getStatus() : request.status());
        updateById(song);
        return toSongResponses(List.of(getById(id))).get(0);
    }

    @Override
    public SongResponse updateSongStatus(Long id, SongStatusRequest request) {
        Song song = getExistingSong(id);
        song.setStatus(request.status());
        updateById(song);
        return toSongResponses(List.of(getById(id))).get(0);
    }

    @Override
    @Transactional
    public void deleteSong(Long id) {
        getExistingSong(id);
        playlistSongService.remove(new LambdaQueryWrapper<PlaylistSong>().eq(PlaylistSong::getSongId, id));
        playHistoryMapper.delete(new LambdaQueryWrapper<PlayHistory>().eq(PlayHistory::getSongId, id));
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getTargetType, TARGET_TYPE_SONG)
                .eq(Favorite::getTargetId, id));
        commentMapper.delete(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getTargetType, TARGET_TYPE_SONG)
                .eq(Comment::getTargetId, id));
        removeById(id);
    }

    private LambdaQueryWrapper<Song> basePublishedWrapper() {
        return new LambdaQueryWrapper<Song>().eq(Song::getStatus, STATUS_PUBLISHED);
    }

    private Song getExistingSong(Long id) {
        Song song = getById(id);
        if (song == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌曲不存在", HttpStatus.NOT_FOUND);
        }
        return song;
    }

    private void validateSongRelations(Long artistId, Long albumId) {
        Artist artist = artistService.getById(artistId);
        if (artist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌手不存在", HttpStatus.NOT_FOUND);
        }
        if (albumId == null) {
            return;
        }
        Album album = albumService.getById(albumId);
        if (album == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "专辑不存在", HttpStatus.NOT_FOUND);
        }
        if (!Objects.equals(album.getArtistId(), artistId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "专辑不属于该歌手", HttpStatus.BAD_REQUEST);
        }
    }

    private void applySongRequest(Song song, SongUpsertRequest request) {
        song.setTitle(request.title());
        song.setArtistId(request.artistId());
        song.setAlbumId(request.albumId());
        song.setCoverUrl(request.coverUrl());
        song.setAudioUrl(request.audioUrl());
        song.setLyricUrl(request.lyricUrl());
        song.setDurationSeconds(request.durationSeconds() == null ? 0 : request.durationSeconds());
        song.setLanguage(request.language());
        song.setGenre(request.genre());
        song.setMood(request.mood());
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

    private PlaylistResponse toPlaylistResponse(Playlist playlist) {
        return new PlaylistResponse(
                playlist.getId(),
                playlist.getUserId(),
                playlist.getTitle(),
                playlist.getDescription(),
                playlist.getCoverUrl(),
                playlist.getVisibility(),
                playlist.getPlayCount(),
                playlist.getFavoriteCount(),
                playlistSongService.count(new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlist.getId())),
                playlist.getCreatedAt(),
                playlist.getUpdatedAt()
        );
    }
}
