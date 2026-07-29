package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.dto.UpdateUserProfileRequest;
import com.musicweb.entity.Comment;
import com.musicweb.entity.Favorite;
import com.musicweb.entity.PlayHistory;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.PlaylistSong;
import com.musicweb.entity.Song;
import com.musicweb.entity.UploadFile;
import com.musicweb.entity.User;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.CommentMapper;
import com.musicweb.mapper.FavoriteMapper;
import com.musicweb.mapper.PlayHistoryMapper;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.mapper.PlaylistSongMapper;
import com.musicweb.mapper.SongMapper;
import com.musicweb.mapper.UploadFileMapper;
import com.musicweb.service.UserAccountService;
import com.musicweb.service.UserService;
import com.musicweb.vo.AdminUserResponse;
import com.musicweb.vo.PlaylistResponse;
import com.musicweb.vo.PublicUserResponse;
import com.musicweb.vo.UserSummaryResponse;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserAccountServiceImpl implements UserAccountService {

    private static final String TARGET_TYPE_PLAYLIST = "PLAYLIST";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final int STATUS_ENABLED = 1;

    private final UserService userService;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final SongMapper songMapper;
    private final FavoriteMapper favoriteMapper;
    private final CommentMapper commentMapper;
    private final PlayHistoryMapper playHistoryMapper;
    private final UploadFileMapper uploadFileMapper;

    public UserAccountServiceImpl(
            UserService userService,
            PlaylistMapper playlistMapper,
            PlaylistSongMapper playlistSongMapper,
            SongMapper songMapper,
            FavoriteMapper favoriteMapper,
            CommentMapper commentMapper,
            PlayHistoryMapper playHistoryMapper,
            UploadFileMapper uploadFileMapper
    ) {
        this.userService = userService;
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.songMapper = songMapper;
        this.favoriteMapper = favoriteMapper;
        this.commentMapper = commentMapper;
        this.playHistoryMapper = playHistoryMapper;
        this.uploadFileMapper = uploadFileMapper;
    }

    @Override
    public PageResult<AdminUserResponse> listUsers(long page, long size, String keyword, String role, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(keyword), User::getUsername, keyword)
                .eq(StringUtils.hasText(role), User::getRole, role)
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getCreatedAt)
                .orderByDesc(User::getId);
        Page<User> userPage = userService.page(new Page<>(page, size), wrapper);
        return new PageResult<>(
                userPage.getRecords().stream().map(AdminUserResponse::from).toList(),
                userPage.getCurrent(),
                userPage.getSize(),
                userPage.getTotal()
        );
    }

    @Override
    public PublicUserResponse getPublicUser(Long userId) {
        User user = getEnabledUser(userId);
        List<Long> publicPlaylistIds = listPublicPlaylistIds(userId);
        long receivedFavoriteCount = publicPlaylistIds.isEmpty()
                ? 0
                : favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getTargetType, TARGET_TYPE_PLAYLIST)
                        .in(Favorite::getTargetId, publicPlaylistIds));
        long commentCount = countPublicComments(userId);
        return new PublicUserResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio(),
                publicPlaylistIds.size(),
                receivedFavoriteCount,
                commentCount
        );
    }

    @Override
    public PageResult<PlaylistResponse> listPublicUserPlaylists(Long userId, long page, long size) {
        getEnabledUser(userId);
        Page<Playlist> playlistPage = playlistMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getUserId, userId)
                        .eq(Playlist::getVisibility, VISIBILITY_PUBLIC)
                        .orderByDesc(Playlist::getUpdatedAt)
                        .orderByDesc(Playlist::getId)
        );
        return new PageResult<>(
                playlistPage.getRecords().stream().map(this::toPlaylistResponse).toList(),
                playlistPage.getCurrent(),
                playlistPage.getSize(),
                playlistPage.getTotal()
        );
    }

    @Override
    public UserSummaryResponse getCurrentUser(Long userId) {
        return UserSummaryResponse.from(getExistingUser(userId));
    }

    @Override
    @Transactional
    public UserSummaryResponse updateProfile(Long userId, UpdateUserProfileRequest request) {
        User user = getExistingUser(userId);
        String nickname = request.nickname().trim();
        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "昵称不能为空", HttpStatus.BAD_REQUEST);
        }
        user.setNickname(nickname);
        user.setAvatarUrl(normalizeNullableText(request.avatarUrl()));
        user.setBio(normalizeNullableText(request.bio()));
        user.setUpdatedAt(LocalDateTime.now());
        userService.updateById(user);
        return UserSummaryResponse.from(userService.getById(userId));
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND);
        }

        List<Long> playlistIds = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>().eq(Playlist::getUserId, userId)
        ).stream().map(Playlist::getId).toList();

        if (!playlistIds.isEmpty()) {
            playlistSongMapper.delete(new LambdaQueryWrapper<PlaylistSong>().in(PlaylistSong::getPlaylistId, playlistIds));
            favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getTargetType, TARGET_TYPE_PLAYLIST)
                    .in(Favorite::getTargetId, playlistIds));
            commentMapper.delete(new LambdaQueryWrapper<Comment>()
                    .eq(Comment::getTargetType, TARGET_TYPE_PLAYLIST)
                    .in(Comment::getTargetId, playlistIds));
            playlistMapper.delete(new LambdaQueryWrapper<Playlist>().in(Playlist::getId, playlistIds));
        }

        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId));
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, userId));
        playHistoryMapper.delete(new LambdaQueryWrapper<PlayHistory>().eq(PlayHistory::getUserId, userId));
        uploadFileMapper.delete(new LambdaQueryWrapper<UploadFile>().eq(UploadFile::getOwnerId, userId));
        userService.removeById(userId);
    }

    private User getEnabledUser(Long userId) {
        User user = userService.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getStatus, STATUS_ENABLED), false);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND);
        }
        return user;
    }

    private User getExistingUser(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND);
        }
        return user;
    }

    private List<Long> listPublicPlaylistIds(Long userId) {
        return playlistMapper.selectList(new LambdaQueryWrapper<Playlist>()
                        .select(Playlist::getId)
                        .eq(Playlist::getUserId, userId)
                        .eq(Playlist::getVisibility, VISIBILITY_PUBLIC))
                .stream()
                .map(Playlist::getId)
                .toList();
    }

    private long countPublicComments(Long userId) {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .select(Comment::getTargetType, Comment::getTargetId)
                .eq(Comment::getUserId, userId)
                .eq(Comment::getStatus, STATUS_ENABLED));
        if (comments.isEmpty()) {
            return 0;
        }

        Set<Long> songIds = new HashSet<>();
        Set<Long> playlistIds = new HashSet<>();
        for (Comment comment : comments) {
            if ("SONG".equals(comment.getTargetType())) {
                songIds.add(comment.getTargetId());
            } else if (TARGET_TYPE_PLAYLIST.equals(comment.getTargetType())) {
                playlistIds.add(comment.getTargetId());
            }
        }

        Set<Long> visibleSongIds = songIds.isEmpty()
                ? Set.of()
                : songMapper.selectList(new LambdaQueryWrapper<Song>()
                        .select(Song::getId)
                        .in(Song::getId, songIds)
                        .eq(Song::getStatus, STATUS_ENABLED))
                        .stream()
                        .map(Song::getId)
                        .collect(java.util.stream.Collectors.toSet());
        Set<Long> visiblePlaylistIds = playlistIds.isEmpty()
                ? Set.of()
                : playlistMapper.selectList(new LambdaQueryWrapper<Playlist>()
                        .select(Playlist::getId)
                        .in(Playlist::getId, playlistIds)
                        .eq(Playlist::getVisibility, VISIBILITY_PUBLIC))
                        .stream()
                        .map(Playlist::getId)
                        .collect(java.util.stream.Collectors.toSet());

        return comments.stream()
                .filter(comment -> "SONG".equals(comment.getTargetType())
                        ? visibleSongIds.contains(comment.getTargetId())
                        : TARGET_TYPE_PLAYLIST.equals(comment.getTargetType())
                                && visiblePlaylistIds.contains(comment.getTargetId()))
                .count();
    }

    private PlaylistResponse toPlaylistResponse(Playlist playlist) {
        long songCount = playlistSongMapper.selectCount(new LambdaQueryWrapper<PlaylistSong>()
                .eq(PlaylistSong::getPlaylistId, playlist.getId()));
        return new PlaylistResponse(
                playlist.getId(),
                playlist.getUserId(),
                playlist.getTitle(),
                playlist.getDescription(),
                playlist.getCoverUrl(),
                playlist.getVisibility(),
                playlist.getPlayCount(),
                playlist.getFavoriteCount(),
                songCount,
                playlist.getCreatedAt(),
                playlist.getUpdatedAt(),
                null,
                null,
                List.of(),
                0L,
                false,
                false
        );
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
