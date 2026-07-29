package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.dto.PlaylistOrderRequest;
import com.musicweb.dto.PlaylistSongRequest;
import com.musicweb.dto.PlaylistSongBatchRequest;
import com.musicweb.dto.PlaylistUpsertRequest;
import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.Comment;
import com.musicweb.entity.Favorite;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.PlaylistSong;
import com.musicweb.entity.PlaylistTag;
import com.musicweb.entity.Song;
import com.musicweb.entity.User;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.mapper.PlaylistTagMapper;
import com.musicweb.mapper.UserMapper;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import com.musicweb.service.CommentService;
import com.musicweb.service.FavoriteService;
import com.musicweb.service.PlaylistService;
import com.musicweb.service.PlaylistSongService;
import com.musicweb.service.SongService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.PlaylistDetailResponse;
import com.musicweb.vo.PlaylistResponse;
import com.musicweb.vo.PlaylistSongResponse;
import com.musicweb.vo.SongResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
public class PlaylistServiceImpl extends ServiceImpl<PlaylistMapper, Playlist> implements PlaylistService {

    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_PRIVATE = "PRIVATE";
    private static final String TARGET_TYPE_PLAYLIST = "PLAYLIST";
    private static final int STATUS_PUBLISHED = 1;

    private final PlaylistSongService playlistSongService;
    private final SongService songService;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final FavoriteService favoriteService;
    private final CommentService commentService;
    private final PlaylistTagMapper playlistTagMapper;
    private final UserMapper userMapper;

    public PlaylistServiceImpl(
            PlaylistSongService playlistSongService,
            SongService songService,
            ArtistService artistService,
            AlbumService albumService,
            FavoriteService favoriteService,
            CommentService commentService,
            PlaylistTagMapper playlistTagMapper,
            UserMapper userMapper
    ) {
        this.playlistSongService = playlistSongService;
        this.songService = songService;
        this.artistService = artistService;
        this.albumService = albumService;
        this.favoriteService = favoriteService;
        this.commentService = commentService;
        this.playlistTagMapper = playlistTagMapper;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<PlaylistResponse> listPublicPlaylists(long page, long size, String keyword, Long currentUserId) {
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getVisibility, VISIBILITY_PUBLIC)
                .like(StringUtils.hasText(keyword), Playlist::getTitle, keyword)
                .orderByDesc(Playlist::getUpdatedAt)
                .orderByDesc(Playlist::getId);
        Page<Playlist> playlistPage = page(new Page<>(page, size), wrapper);
        return toPlaylistPageResult(playlistPage, currentUserId);
    }

    @Override
    public PageResult<PlaylistResponse> listUserPlaylists(Long userId, long page, long size) {
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<Playlist>()
                .eq(Playlist::getUserId, userId)
                .orderByDesc(Playlist::getUpdatedAt)
                .orderByDesc(Playlist::getId);
        Page<Playlist> playlistPage = page(new Page<>(page, size), wrapper);
        return toPlaylistPageResult(playlistPage, userId);
    }

    @Override
    public PlaylistDetailResponse getPlaylist(Long id, Long currentUserId) {
        Playlist playlist = getVisiblePlaylist(id, currentUserId);
        return toPlaylistDetail(playlist, currentUserId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse createPlaylist(PlaylistUpsertRequest request, Long userId) {
        Playlist playlist = new Playlist();
        playlist.setUserId(userId);
        applyPlaylistRequest(playlist, request);
        playlist.setPlayCount(0L);
        playlist.setFavoriteCount(0L);
        save(playlist);
        replaceTags(playlist.getId(), request.tags());
        return toPlaylistDetail(getById(playlist.getId()), userId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse updatePlaylist(Long id, PlaylistUpsertRequest request, Long userId) {
        Playlist playlist = getOwnedPlaylist(id, userId);
        boolean becomesPrivate = VISIBILITY_PUBLIC.equals(playlist.getVisibility())
                && VISIBILITY_PRIVATE.equals(normalizeVisibility(request.visibility()));
        applyPlaylistRequest(playlist, request);
        updateById(playlist);
        replaceTags(id, request.tags());
        if (becomesPrivate) {
            favoriteService.remove(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getTargetType, TARGET_TYPE_PLAYLIST)
                    .eq(Favorite::getTargetId, id));
            baseMapper.setFavoriteCount(id, 0);
        }
        return toPlaylistDetail(getById(id), userId);
    }

    @Override
    @Transactional
    public void deletePlaylist(Long id, Long userId) {
        Playlist playlist = getOwnedPlaylist(id, userId);
        playlistSongService.remove(new LambdaQueryWrapper<PlaylistSong>().eq(PlaylistSong::getPlaylistId, id));
        favoriteService.remove(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getTargetType, TARGET_TYPE_PLAYLIST)
                .eq(Favorite::getTargetId, id));
        commentService.remove(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getTargetType, TARGET_TYPE_PLAYLIST)
                .eq(Comment::getTargetId, id));
        playlistTagMapper.delete(new LambdaQueryWrapper<PlaylistTag>().eq(PlaylistTag::getPlaylistId, id));
        removeById(playlist.getId());
    }

    @Override
    @Transactional
    public PlaylistDetailResponse addSong(Long playlistId, PlaylistSongRequest request, Long userId) {
        Playlist playlist = getOwnedPlaylist(playlistId, userId);
        validatePublishedSong(request.songId());

        PlaylistSong existing = playlistSongService.getOne(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .eq(PlaylistSong::getSongId, request.songId()),
                false
        );
        if (existing == null) {
            PlaylistSong playlistSong = new PlaylistSong();
            playlistSong.setPlaylistId(playlistId);
            playlistSong.setSongId(request.songId());
            playlistSong.setSortOrder(nextSortOrder(playlistId));
            playlistSongService.save(playlistSong);
        }
        return toPlaylistDetail(playlist, userId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse removeSong(Long playlistId, Long songId, Long userId) {
        Playlist playlist = getOwnedPlaylist(playlistId, userId);
        playlistSongService.remove(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlistId)
                .eq(PlaylistSong::getSongId, songId));
        normalizeSortOrder(playlistId);
        return toPlaylistDetail(playlist, userId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse reorderSongs(Long playlistId, PlaylistOrderRequest request, Long userId) {
        Playlist playlist = getOwnedPlaylist(playlistId, userId);
        List<PlaylistSong> playlistSongs = listPlaylistSongs(playlistId);
        List<Long> requestedSongIds = request.songIds();
        Set<Long> requestedSet = new HashSet<>(requestedSongIds);
        Set<Long> existingSet = playlistSongs.stream().map(PlaylistSong::getSongId).collect(Collectors.toSet());
        if (requestedSet.size() != requestedSongIds.size() || !requestedSet.equals(existingSet)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "排序歌曲必须与当前歌单歌曲一致", HttpStatus.BAD_REQUEST);
        }

        Map<Long, PlaylistSong> relationsBySongId = playlistSongs.stream()
                .collect(Collectors.toMap(PlaylistSong::getSongId, Function.identity()));
        for (int index = 0; index < requestedSongIds.size(); index++) {
            PlaylistSong relation = relationsBySongId.get(requestedSongIds.get(index));
            relation.setSortOrder(index + 1);
            playlistSongService.updateById(relation);
        }
        return toPlaylistDetail(playlist, userId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse addSongs(Long playlistId, PlaylistSongBatchRequest request, Long userId) {
        Playlist playlist = getOwnedPlaylist(playlistId, userId);
        List<Long> songIds = distinctSongIds(request.songIds());
        Map<Long, Song> publishedSongs = songService.listByIds(songIds).stream()
                .filter(song -> Objects.equals(song.getStatus(), STATUS_PUBLISHED))
                .collect(Collectors.toMap(Song::getId, Function.identity()));
        if (publishedSongs.size() != songIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "批量歌曲中包含不存在或已下架内容", HttpStatus.NOT_FOUND);
        }
        Set<Long> existingIds = playlistSongService.list(new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .in(PlaylistSong::getSongId, songIds))
                .stream()
                .map(PlaylistSong::getSongId)
                .collect(Collectors.toSet());
        int sortOrder = nextSortOrder(playlistId);
        for (Long songId : songIds) {
            if (existingIds.contains(songId)) {
                continue;
            }
            PlaylistSong relation = new PlaylistSong();
            relation.setPlaylistId(playlistId);
            relation.setSongId(songId);
            relation.setSortOrder(sortOrder++);
            playlistSongService.save(relation);
        }
        return toPlaylistDetail(playlist, userId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse removeSongs(Long playlistId, PlaylistSongBatchRequest request, Long userId) {
        Playlist playlist = getOwnedPlaylist(playlistId, userId);
        List<Long> songIds = distinctSongIds(request.songIds());
        playlistSongService.remove(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlistId)
                .in(PlaylistSong::getSongId, songIds));
        normalizeSortOrder(playlistId);
        return toPlaylistDetail(playlist, userId);
    }

    @Override
    @Transactional
    public PlaylistDetailResponse recordPlay(Long playlistId, Long currentUserId) {
        Playlist playlist = getVisiblePlaylist(playlistId, currentUserId);
        baseMapper.incrementPlayCount(playlistId);
        return toPlaylistDetail(getById(playlistId), currentUserId);
    }

    private void applyPlaylistRequest(Playlist playlist, PlaylistUpsertRequest request) {
        playlist.setTitle(request.title().trim());
        playlist.setDescription(normalizeNullableText(request.description()));
        playlist.setCoverUrl(normalizeNullableText(request.coverUrl()));
        playlist.setVisibility(normalizeVisibility(request.visibility()));
    }

    private void replaceTags(Long playlistId, List<String> requestedTags) {
        List<String> tags = normalizeTags(requestedTags);
        playlistTagMapper.delete(new LambdaQueryWrapper<PlaylistTag>().eq(PlaylistTag::getPlaylistId, playlistId));
        for (int index = 0; index < tags.size(); index++) {
            PlaylistTag relation = new PlaylistTag();
            relation.setPlaylistId(playlistId);
            relation.setTag(tags.get(index));
            relation.setSortOrder(index + 1);
            playlistTagMapper.insert(relation);
        }
    }

    private List<String> normalizeTags(List<String> requestedTags) {
        if (requestedTags == null || requestedTags.isEmpty()) {
            return List.of();
        }
        List<String> tags = requestedTags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (tags.size() > 5 || tags.stream().anyMatch(tag -> tag.codePointCount(0, tag.length()) > 12)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "歌单标签最多 5 个且每个不超过 12 字", HttpStatus.BAD_REQUEST);
        }
        return tags;
    }

    private List<Long> distinctSongIds(List<Long> songIds) {
        List<Long> distinct = songIds.stream().distinct().toList();
        if (distinct.size() != songIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "批量歌曲不能重复", HttpStatus.BAD_REQUEST);
        }
        return distinct;
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeVisibility(String visibility) {
        if (!StringUtils.hasText(visibility)) {
            return VISIBILITY_PUBLIC;
        }
        String normalized = visibility.trim().toUpperCase();
        if (!VISIBILITY_PUBLIC.equals(normalized) && !VISIBILITY_PRIVATE.equals(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "歌单可见性仅支持 PUBLIC 或 PRIVATE", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private Playlist getVisiblePlaylist(Long id, Long currentUserId) {
        Playlist playlist = getExistingPlaylist(id);
        if (VISIBILITY_PUBLIC.equals(playlist.getVisibility()) || Objects.equals(playlist.getUserId(), currentUserId)) {
            return playlist;
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "歌单不存在或不可访问", HttpStatus.NOT_FOUND);
    }

    private Playlist getOwnedPlaylist(Long id, Long userId) {
        Playlist playlist = getExistingPlaylist(id);
        if (!Objects.equals(playlist.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能操作自己的歌单", HttpStatus.FORBIDDEN);
        }
        return playlist;
    }

    private Playlist getExistingPlaylist(Long id) {
        Playlist playlist = getById(id);
        if (playlist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌单不存在", HttpStatus.NOT_FOUND);
        }
        return playlist;
    }

    private Song validatePublishedSong(Long songId) {
        Song song = songService.getById(songId);
        if (song == null || !Objects.equals(song.getStatus(), STATUS_PUBLISHED)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌曲不存在或已下架", HttpStatus.NOT_FOUND);
        }
        return song;
    }

    private int nextSortOrder(Long playlistId) {
        PlaylistSong last = playlistSongService.getOne(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .orderByDesc(PlaylistSong::getSortOrder)
                        .last("LIMIT 1"),
                false
        );
        return last == null ? 1 : last.getSortOrder() + 1;
    }

    private void normalizeSortOrder(Long playlistId) {
        List<PlaylistSong> playlistSongs = listPlaylistSongs(playlistId);
        for (int index = 0; index < playlistSongs.size(); index++) {
            PlaylistSong playlistSong = playlistSongs.get(index);
            playlistSong.setSortOrder(index + 1);
            playlistSongService.updateById(playlistSong);
        }
    }

    private PageResult<PlaylistResponse> toPlaylistPageResult(Page<Playlist> page, Long currentUserId) {
        return new PageResult<>(
                toPlaylistResponses(page.getRecords(), currentUserId),
                page.getCurrent(),
                page.getSize(),
                page.getTotal()
        );
    }

    private List<PlaylistResponse> toPlaylistResponses(List<Playlist> playlists, Long currentUserId) {
        if (playlists.isEmpty()) {
            return List.of();
        }
        List<Long> playlistIds = playlists.stream().map(Playlist::getId).toList();
        Map<Long, Long> songCounts = playlistSongService.list(new LambdaQueryWrapper<PlaylistSong>()
                        .in(PlaylistSong::getPlaylistId, playlistIds))
                .stream()
                .collect(Collectors.groupingBy(PlaylistSong::getPlaylistId, Collectors.counting()));
        Map<Long, List<String>> tagsByPlaylist = playlistTagMapper.selectList(new LambdaQueryWrapper<PlaylistTag>()
                        .in(PlaylistTag::getPlaylistId, playlistIds)
                        .orderByAsc(PlaylistTag::getSortOrder)
                        .orderByAsc(PlaylistTag::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        PlaylistTag::getPlaylistId,
                        Collectors.mapping(PlaylistTag::getTag, Collectors.toList())
                ));
        Map<Long, Long> commentCounts = commentService.list(new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getTargetType, TARGET_TYPE_PLAYLIST)
                        .in(Comment::getTargetId, playlistIds)
                        .isNull(Comment::getParentId)
                        .eq(Comment::getStatus, 1))
                .stream()
                .collect(Collectors.groupingBy(Comment::getTargetId, Collectors.counting()));
        Map<Long, User> creators = userMapper.selectBatchIds(
                        playlists.stream().map(Playlist::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> favoriteIds = currentUserId == null
                ? Set.of()
                : favoriteService.list(new LambdaQueryWrapper<Favorite>()
                                .eq(Favorite::getUserId, currentUserId)
                                .eq(Favorite::getTargetType, TARGET_TYPE_PLAYLIST)
                                .in(Favorite::getTargetId, playlistIds))
                        .stream()
                        .map(Favorite::getTargetId)
                        .collect(Collectors.toSet());
        return playlists.stream().map(playlist -> {
            User creator = creators.get(playlist.getUserId());
            return new PlaylistResponse(
                    playlist.getId(),
                    playlist.getUserId(),
                    playlist.getTitle(),
                    playlist.getDescription(),
                    playlist.getCoverUrl(),
                    playlist.getVisibility(),
                    playlist.getPlayCount(),
                    playlist.getFavoriteCount(),
                    songCounts.getOrDefault(playlist.getId(), 0L),
                    playlist.getCreatedAt(),
                    playlist.getUpdatedAt(),
                    creator == null ? "已注销用户" : creator.getNickname(),
                    creator == null ? null : creator.getAvatarUrl(),
                    tagsByPlaylist.getOrDefault(playlist.getId(), List.of()),
                    VISIBILITY_PUBLIC.equals(playlist.getVisibility())
                            ? commentCounts.getOrDefault(playlist.getId(), 0L)
                            : 0L,
                    favoriteIds.contains(playlist.getId()),
                    currentUserId != null && Objects.equals(playlist.getUserId(), currentUserId)
            );
        }).toList();
    }

    private PlaylistDetailResponse toPlaylistDetail(Playlist playlist, Long currentUserId) {
        PlaylistResponse summary = toPlaylistResponses(List.of(playlist), currentUserId).get(0);
        return new PlaylistDetailResponse(
                playlist.getId(),
                playlist.getUserId(),
                playlist.getTitle(),
                playlist.getDescription(),
                playlist.getCoverUrl(),
                playlist.getVisibility(),
                playlist.getPlayCount(),
                playlist.getFavoriteCount(),
                summary.songCount(),
                playlist.getCreatedAt(),
                playlist.getUpdatedAt(),
                summary.creatorNickname(),
                summary.creatorAvatarUrl(),
                summary.tags(),
                summary.commentCount(),
                summary.favorited(),
                summary.canManage(),
                toPlaylistSongResponses(listPlaylistSongs(playlist.getId()))
        );
    }

    private List<PlaylistSong> listPlaylistSongs(Long playlistId) {
        return playlistSongService.list(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlistId)
                .orderByAsc(PlaylistSong::getSortOrder)
                .orderByAsc(PlaylistSong::getId));
    }

    private List<PlaylistSongResponse> toPlaylistSongResponses(List<PlaylistSong> playlistSongs) {
        if (playlistSongs.isEmpty()) {
            return List.of();
        }
        List<Long> songIds = playlistSongs.stream().map(PlaylistSong::getSongId).toList();
        Map<Long, Song> songsById = songService.listByIds(songIds).stream()
                .filter(song -> Objects.equals(song.getStatus(), STATUS_PUBLISHED))
                .collect(Collectors.toMap(Song::getId, Function.identity()));
        Map<Long, Artist> artistsById = loadArtistsByIds(
                songsById.values().stream().map(Song::getArtistId).collect(Collectors.toSet())
        );
        Map<Long, Album> albumsById = loadAlbumsByIds(
                songsById.values().stream().map(Song::getAlbumId).filter(Objects::nonNull).collect(Collectors.toSet())
        );

        List<PlaylistSongResponse> responses = new ArrayList<>();
        for (PlaylistSong playlistSong : playlistSongs) {
            Song song = songsById.get(playlistSong.getSongId());
            if (song == null) {
                continue;
            }
            SongResponse songResponse = MusicResponseAssembler.toSongResponse(song, artistsById, albumsById);
            responses.add(new PlaylistSongResponse(
                    playlistSong.getId(),
                    playlistSong.getSongId(),
                    playlistSong.getSortOrder(),
                    songResponse,
                    playlistSong.getCreatedAt()
            ));
        }
        return responses;
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
