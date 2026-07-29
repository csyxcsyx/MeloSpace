package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.entity.Comment;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.Song;
import com.musicweb.entity.User;
import com.musicweb.mapper.CommentMapper;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.mapper.SongMapper;
import com.musicweb.mapper.UserMapper;
import com.musicweb.service.DiscoverService;
import com.musicweb.service.PlaylistService;
import com.musicweb.vo.CommunityDiscoverResponse;
import com.musicweb.vo.DiscoverCommentResponse;
import com.musicweb.vo.PlaylistResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DiscoverServiceImpl implements DiscoverService {

    private static final String TARGET_SONG = "SONG";
    private static final String TARGET_PLAYLIST = "PLAYLIST";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final int STATUS_VISIBLE = 1;
    private static final int SECTION_SIZE = 8;

    private final PlaylistService playlistService;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final SongMapper songMapper;
    private final PlaylistMapper playlistMapper;

    public DiscoverServiceImpl(
            PlaylistService playlistService,
            CommentMapper commentMapper,
            UserMapper userMapper,
            SongMapper songMapper,
            PlaylistMapper playlistMapper
    ) {
        this.playlistService = playlistService;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.songMapper = songMapper;
        this.playlistMapper = playlistMapper;
    }

    @Override
    public CommunityDiscoverResponse community(Long currentUserId) {
        List<PlaylistResponse> publicPlaylists =
                playlistService.listPublicPlaylists(1, 100, null, currentUserId).items();
        List<PlaylistResponse> popular = publicPlaylists.stream()
                .sorted(Comparator.comparingLong(DiscoverServiceImpl::playlistScore).reversed()
                        .thenComparing(PlaylistResponse::updatedAt, Comparator.reverseOrder())
                        .thenComparing(PlaylistResponse::id, Comparator.reverseOrder()))
                .limit(SECTION_SIZE)
                .toList();
        List<PlaylistResponse> latest = publicPlaylists.stream()
                .sorted(Comparator.comparing(PlaylistResponse::updatedAt, Comparator.reverseOrder())
                        .thenComparing(PlaylistResponse::id, Comparator.reverseOrder()))
                .limit(SECTION_SIZE)
                .toList();
        return new CommunityDiscoverResponse(popular, latest, loadHotComments());
    }

    private List<DiscoverCommentResponse> loadHotComments() {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, STATUS_VISIBLE)
                .isNull(Comment::getParentId)
                .isNull(Comment::getDeletedAt)
                .in(Comment::getTargetType, List.of(TARGET_SONG, TARGET_PLAYLIST))
                .orderByDesc(Comment::getCreatedAt)
                .last("LIMIT 100"));
        if (comments.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        Set<Long> songIds = comments.stream()
                .filter(comment -> TARGET_SONG.equals(comment.getTargetType()))
                .map(Comment::getTargetId)
                .collect(Collectors.toSet());
        Set<Long> playlistIds = comments.stream()
                .filter(comment -> TARGET_PLAYLIST.equals(comment.getTargetType()))
                .map(Comment::getTargetId)
                .collect(Collectors.toSet());
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Song> songs = songIds.isEmpty() ? Map.of() : songMapper.selectBatchIds(songIds).stream()
                .filter(song -> Objects.equals(song.getStatus(), STATUS_VISIBLE))
                .collect(Collectors.toMap(Song::getId, Function.identity()));
        Map<Long, Playlist> playlists = playlistIds.isEmpty() ? Map.of() : playlistMapper.selectBatchIds(playlistIds).stream()
                .filter(playlist -> VISIBILITY_PUBLIC.equals(playlist.getVisibility()))
                .collect(Collectors.toMap(Playlist::getId, Function.identity()));

        return comments.stream()
                .filter(comment -> TARGET_SONG.equals(comment.getTargetType())
                        ? songs.containsKey(comment.getTargetId())
                        : playlists.containsKey(comment.getTargetId()))
                .sorted(Comparator.comparingLong(DiscoverServiceImpl::commentScore).reversed()
                        .thenComparing(Comment::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(Comment::getId, Comparator.reverseOrder()))
                .limit(SECTION_SIZE)
                .map(comment -> toResponse(comment, users, songs, playlists))
                .toList();
    }

    private DiscoverCommentResponse toResponse(
            Comment comment,
            Map<Long, User> users,
            Map<Long, Song> songs,
            Map<Long, Playlist> playlists
    ) {
        User user = users.get(comment.getUserId());
        Song song = songs.get(comment.getTargetId());
        Playlist playlist = playlists.get(comment.getTargetId());
        return new DiscoverCommentResponse(
                comment.getId(),
                comment.getUserId(),
                user == null ? "已注销用户" : user.getNickname(),
                user == null ? null : user.getAvatarUrl(),
                comment.getTargetType(),
                comment.getTargetId(),
                song == null ? playlist.getTitle() : song.getTitle(),
                song == null ? playlist.getCoverUrl() : song.getCoverUrl(),
                comment.getContent(),
                safeCount(comment.getLikeCount()),
                safeCount(comment.getReplyCount()),
                comment.getCreatedAt()
        );
    }

    private static long playlistScore(PlaylistResponse playlist) {
        return safeLong(playlist.favoriteCount()) * 4L
                + safeLong(playlist.commentCount()) * 3L
                + safeLong(playlist.playCount());
    }

    private static long commentScore(Comment comment) {
        return safeCount(comment.getLikeCount()) * 3L + safeCount(comment.getReplyCount()) * 2L;
    }

    private static int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
