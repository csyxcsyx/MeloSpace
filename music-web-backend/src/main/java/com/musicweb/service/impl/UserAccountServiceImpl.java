package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.entity.Comment;
import com.musicweb.entity.Favorite;
import com.musicweb.entity.PlayHistory;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.PlaylistSong;
import com.musicweb.entity.UploadFile;
import com.musicweb.entity.User;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.CommentMapper;
import com.musicweb.mapper.FavoriteMapper;
import com.musicweb.mapper.PlayHistoryMapper;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.mapper.PlaylistSongMapper;
import com.musicweb.mapper.UploadFileMapper;
import com.musicweb.service.UserAccountService;
import com.musicweb.service.UserService;
import com.musicweb.vo.AdminUserResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserAccountServiceImpl implements UserAccountService {

    private static final String TARGET_TYPE_PLAYLIST = "PLAYLIST";

    private final UserService userService;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final FavoriteMapper favoriteMapper;
    private final CommentMapper commentMapper;
    private final PlayHistoryMapper playHistoryMapper;
    private final UploadFileMapper uploadFileMapper;

    public UserAccountServiceImpl(
            UserService userService,
            PlaylistMapper playlistMapper,
            PlaylistSongMapper playlistSongMapper,
            FavoriteMapper favoriteMapper,
            CommentMapper commentMapper,
            PlayHistoryMapper playHistoryMapper,
            UploadFileMapper uploadFileMapper
    ) {
        this.userService = userService;
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
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
}
