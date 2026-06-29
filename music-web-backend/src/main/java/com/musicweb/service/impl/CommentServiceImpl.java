package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.dto.CommentRequest;
import com.musicweb.entity.Comment;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.Song;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.CommentMapper;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.service.CommentService;
import com.musicweb.service.SongService;
import com.musicweb.vo.CommentResponse;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private static final String TARGET_TYPE_SONG = "SONG";
    private static final String TARGET_TYPE_PLAYLIST = "PLAYLIST";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final int STATUS_PUBLISHED = 1;
    private static final int COMMENT_VISIBLE = 1;

    private final SongService songService;
    private final PlaylistMapper playlistMapper;

    public CommentServiceImpl(SongService songService, PlaylistMapper playlistMapper) {
        this.songService = songService;
        this.playlistMapper = playlistMapper;
    }

    @Override
    public PageResult<CommentResponse> listComments(String targetType, Long targetId, long page, long size) {
        String normalizedTargetType = normalizeTargetType(targetType);
        validateTarget(normalizedTargetType, targetId, null);
        Page<Comment> commentPage = page(
                new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getTargetType, normalizedTargetType)
                        .eq(Comment::getTargetId, targetId)
                        .eq(Comment::getStatus, COMMENT_VISIBLE)
                        .orderByDesc(Comment::getCreatedAt)
                        .orderByDesc(Comment::getId)
        );
        return new PageResult<>(
                commentPage.getRecords().stream().map(this::toResponse).toList(),
                commentPage.getCurrent(),
                commentPage.getSize(),
                commentPage.getTotal()
        );
    }

    @Override
    @Transactional
    public CommentResponse createComment(CommentRequest request, Long userId) {
        String targetType = normalizeTargetType(request.targetType());
        validateTarget(targetType, request.targetId(), userId);

        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setTargetType(targetType);
        comment.setTargetId(request.targetId());
        comment.setContent(request.content());
        comment.setStatus(COMMENT_VISIBLE);
        save(comment);
        return toResponse(getById(comment.getId()));
    }

    @Override
    @Transactional
    public void deleteComment(Long id, Long userId) {
        Comment comment = getById(id);
        if (comment == null || !Objects.equals(comment.getStatus(), COMMENT_VISIBLE)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在", HttpStatus.NOT_FOUND);
        }
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论", HttpStatus.FORBIDDEN);
        }
        removeById(id);
    }

    private String normalizeTargetType(String targetType) {
        if (!StringUtils.hasText(targetType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评论目标类型不能为空", HttpStatus.BAD_REQUEST);
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
        if (playlist == null ||
                (!VISIBILITY_PUBLIC.equals(playlist.getVisibility()) && !Objects.equals(playlist.getUserId(), userId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌单不存在或不可访问", HttpStatus.NOT_FOUND);
        }
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getUserId(),
                comment.getTargetType(),
                comment.getTargetId(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
