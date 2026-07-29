package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.dto.FavoriteRequest;
import com.musicweb.entity.Favorite;
import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.Comment;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.PlaylistSong;
import com.musicweb.entity.PlaylistTag;
import com.musicweb.entity.Song;
import com.musicweb.entity.User;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.FavoriteMapper;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.mapper.PlaylistTagMapper;
import com.musicweb.mapper.UserMapper;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import com.musicweb.service.CommentService;
import com.musicweb.service.FavoriteService;
import com.musicweb.service.PlaylistSongService;
import com.musicweb.service.SongService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.FavoriteResponse;
import com.musicweb.vo.PlaylistResponse;
import com.musicweb.vo.SongResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
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
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    private static final String TARGET_TYPE_SONG = "SONG";
    private static final String TARGET_TYPE_PLAYLIST = "PLAYLIST";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final int STATUS_PUBLISHED = 1;

    private final SongService songService;
    private final PlaylistMapper playlistMapper;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final PlaylistSongService playlistSongService;
    private final PlaylistTagMapper playlistTagMapper;
    private final UserMapper userMapper;
    private final CommentService commentService;

    public FavoriteServiceImpl(
            SongService songService,
            PlaylistMapper playlistMapper,
            ArtistService artistService,
            AlbumService albumService,
            PlaylistSongService playlistSongService,
            PlaylistTagMapper playlistTagMapper,
            UserMapper userMapper,
            CommentService commentService
    ) {
        this.songService = songService;
        this.playlistMapper = playlistMapper;
        this.artistService = artistService;
        this.albumService = albumService;
        this.playlistSongService = playlistSongService;
        this.playlistTagMapper = playlistTagMapper;
        this.userMapper = userMapper;
        this.commentService = commentService;
    }

    @Override
    @Transactional
    public FavoriteResponse favorite(FavoriteRequest request, Long userId) {
        String targetType = normalizeTargetType(request.targetType());
        validateTarget(targetType, request.targetId(), userId);

        Favorite existing = getOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .eq(Favorite::getTargetId, request.targetId()), false);
        if (existing != null) {
            return toResponses(List.of(existing)).get(0);
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetType(targetType);
        favorite.setTargetId(request.targetId());
        save(favorite);
        if (TARGET_TYPE_PLAYLIST.equals(targetType)) {
            playlistMapper.incrementFavoriteCount(request.targetId());
        }
        return toResponses(List.of(getById(favorite.getId()))).get(0);
    }

    @Override
    @Transactional
    public void unfavorite(String targetType, Long targetId, Long userId) {
        String normalizedTargetType = normalizeTargetType(targetType);
        int removed = getBaseMapper().delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, normalizedTargetType)
                .eq(Favorite::getTargetId, targetId));
        if (removed > 0 && TARGET_TYPE_PLAYLIST.equals(normalizedTargetType)) {
            playlistMapper.decrementFavoriteCount(targetId);
        }
    }

    @Override
    public PageResult<FavoriteResponse> listUserFavorites(Long userId, long page, long size) {
        Page<Favorite> favoritePage = page(
                new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreatedAt)
                        .orderByDesc(Favorite::getId)
        );
        return new PageResult<>(
                toResponses(favoritePage.getRecords()),
                favoritePage.getCurrent(),
                favoritePage.getSize(),
                favoritePage.getTotal()
        );
    }

    @Override
    public Map<Long, Boolean> favoriteStatuses(String targetType, List<Long> targetIds, Long userId) {
        String normalizedTargetType = normalizeTargetType(targetType);
        List<Long> distinctIds = targetIds.stream().distinct().toList();
        Set<Long> favoriteIds = list(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getTargetType, normalizedTargetType)
                        .in(Favorite::getTargetId, distinctIds))
                .stream()
                .map(Favorite::getTargetId)
                .collect(Collectors.toSet());
        Map<Long, Boolean> statuses = new LinkedHashMap<>();
        distinctIds.forEach(id -> statuses.put(id, favoriteIds.contains(id)));
        return statuses;
    }

    private String normalizeTargetType(String targetType) {
        if (!StringUtils.hasText(targetType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "收藏目标类型不能为空", HttpStatus.BAD_REQUEST);
        }
        String normalized = targetType.trim().toUpperCase();
        if (!TARGET_TYPE_SONG.equals(normalized) && !TARGET_TYPE_PLAYLIST.equals(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标类型仅支持 SONG 或 PLAYLIST", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private void validateTarget(String targetType, Long targetId, Long userId) {
        if (TARGET_TYPE_SONG.equals(targetType)) {
            Song song = songService.getById(targetId);
            if (song == null || !Objects.equals(song.getStatus(), STATUS_PUBLISHED)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "歌曲不存在或已下架", HttpStatus.NOT_FOUND);
            }
            return;
        }
        Playlist playlist = playlistMapper.selectById(targetId);
        if (playlist == null || !VISIBILITY_PUBLIC.equals(playlist.getVisibility())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌单不存在或不可访问", HttpStatus.NOT_FOUND);
        }
    }

    private List<FavoriteResponse> toResponses(List<Favorite> favorites) {
        if (favorites.isEmpty()) {
            return List.of();
        }
        Set<Long> songIds = favorites.stream()
                .filter(favorite -> TARGET_TYPE_SONG.equals(favorite.getTargetType()))
                .map(Favorite::getTargetId)
                .collect(Collectors.toSet());
        Set<Long> playlistIds = favorites.stream()
                .filter(favorite -> TARGET_TYPE_PLAYLIST.equals(favorite.getTargetType()))
                .map(Favorite::getTargetId)
                .collect(Collectors.toSet());
        Map<Long, Song> songs = songIds.isEmpty()
                ? Map.of()
                : songService.listByIds(songIds).stream()
                        .filter(song -> Objects.equals(song.getStatus(), STATUS_PUBLISHED))
                        .collect(Collectors.toMap(Song::getId, Function.identity()));
        Map<Long, Artist> artists = loadArtists(
                songs.values().stream().map(Song::getArtistId).collect(Collectors.toSet()));
        Map<Long, Album> albums = loadAlbums(
                songs.values().stream().map(Song::getAlbumId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Playlist> playlists = playlistIds.isEmpty()
                ? Map.of()
                : playlistMapper.selectBatchIds(playlistIds).stream()
                        .filter(playlist -> VISIBILITY_PUBLIC.equals(playlist.getVisibility()))
                        .collect(Collectors.toMap(Playlist::getId, Function.identity()));
        Map<Long, User> creators = playlists.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(playlists.values().stream().map(Playlist::getUserId).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Long> songCounts = playlistIds.isEmpty()
                ? Map.of()
                : playlistSongService.list(new LambdaQueryWrapper<PlaylistSong>()
                                .in(PlaylistSong::getPlaylistId, playlistIds))
                        .stream()
                        .collect(Collectors.groupingBy(PlaylistSong::getPlaylistId, Collectors.counting()));
        Map<Long, List<String>> tags = playlistIds.isEmpty()
                ? Map.of()
                : playlistTagMapper.selectList(new LambdaQueryWrapper<PlaylistTag>()
                                .in(PlaylistTag::getPlaylistId, playlistIds)
                                .orderByAsc(PlaylistTag::getSortOrder))
                        .stream()
                        .collect(Collectors.groupingBy(
                                PlaylistTag::getPlaylistId,
                                Collectors.mapping(PlaylistTag::getTag, Collectors.toList())
                        ));
        Map<Long, Long> commentCounts = playlistIds.isEmpty()
                ? Map.of()
                : commentService.list(new LambdaQueryWrapper<Comment>()
                                .eq(Comment::getTargetType, TARGET_TYPE_PLAYLIST)
                                .in(Comment::getTargetId, playlistIds)
                                .isNull(Comment::getParentId)
                                .eq(Comment::getStatus, 1))
                        .stream()
                        .collect(Collectors.groupingBy(Comment::getTargetId, Collectors.counting()));

        return favorites.stream().map(favorite -> {
            SongResponse song = null;
            PlaylistResponse playlist = null;
            if (TARGET_TYPE_SONG.equals(favorite.getTargetType())) {
                Song entity = songs.get(favorite.getTargetId());
                if (entity != null) {
                    song = MusicResponseAssembler.toSongResponse(entity, artists, albums);
                }
            } else {
                Playlist entity = playlists.get(favorite.getTargetId());
                if (entity != null) {
                    User creator = creators.get(entity.getUserId());
                    playlist = new PlaylistResponse(
                            entity.getId(),
                            entity.getUserId(),
                            entity.getTitle(),
                            entity.getDescription(),
                            entity.getCoverUrl(),
                            entity.getVisibility(),
                            entity.getPlayCount(),
                            entity.getFavoriteCount(),
                            songCounts.getOrDefault(entity.getId(), 0L),
                            entity.getCreatedAt(),
                            entity.getUpdatedAt(),
                            creator == null ? "已注销用户" : creator.getNickname(),
                            creator == null ? null : creator.getAvatarUrl(),
                            tags.getOrDefault(entity.getId(), List.of()),
                            commentCounts.getOrDefault(entity.getId(), 0L),
                            true,
                            Objects.equals(entity.getUserId(), favorite.getUserId())
                    );
                }
            }
            return new FavoriteResponse(
                    favorite.getId(),
                    favorite.getUserId(),
                    favorite.getTargetType(),
                    favorite.getTargetId(),
                    favorite.getCreatedAt(),
                    song,
                    playlist
            );
        }).toList();
    }

    private Map<Long, Artist> loadArtists(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return artistService.listByIds(ids).stream().collect(Collectors.toMap(Artist::getId, Function.identity()));
    }

    private Map<Long, Album> loadAlbums(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return albumService.listByIds(ids).stream().collect(Collectors.toMap(Album::getId, Function.identity()));
    }
}
