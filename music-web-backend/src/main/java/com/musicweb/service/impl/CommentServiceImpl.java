package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.dto.AdminCommentActionRequest;
import com.musicweb.dto.CommentReportRequest;
import com.musicweb.dto.CommentRequest;
import com.musicweb.entity.Comment;
import com.musicweb.entity.CommentLike;
import com.musicweb.entity.CommentReport;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.Song;
import com.musicweb.entity.User;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.CommentLikeMapper;
import com.musicweb.mapper.CommentMapper;
import com.musicweb.mapper.CommentReportMapper;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.mapper.UserMapper;
import com.musicweb.service.CommentService;
import com.musicweb.service.SongService;
import com.musicweb.vo.CommentReportResponse;
import com.musicweb.vo.CommentResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private static final String TARGET_TYPE_SONG = "SONG";
    private static final String TARGET_TYPE_PLAYLIST = "PLAYLIST";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final int STATUS_HIDDEN = 0;
    private static final int STATUS_VISIBLE = 1;
    private static final int STATUS_DELETED = 2;
    private static final int STATUS_PUBLISHED = 1;
    private static final int MAX_COMMENTS_PER_MINUTE = 5;
    private static final int MAX_REPORTS_PER_DAY = 10;
    private static final Set<String> REPORT_REASONS = Set.of("SPAM", "ABUSE", "HARASSMENT", "COPYRIGHT", "OTHER");
    private static final Set<String> MODERATION_ACTIONS = Set.of("HIDE", "RESTORE", "PIN", "UNPIN");

    private final SongService songService;
    private final PlaylistMapper playlistMapper;
    private final UserMapper userMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final CommentReportMapper commentReportMapper;

    public CommentServiceImpl(
            SongService songService,
            PlaylistMapper playlistMapper,
            UserMapper userMapper,
            CommentLikeMapper commentLikeMapper,
            CommentReportMapper commentReportMapper
    ) {
        this.songService = songService;
        this.playlistMapper = playlistMapper;
        this.userMapper = userMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.commentReportMapper = commentReportMapper;
    }

    @Override
    public PageResult<CommentResponse> listComments(
            String targetType,
            Long targetId,
            String sort,
            long page,
            long size,
            Long viewerUserId
    ) {
        String normalizedTargetType = normalizeTargetType(targetType);
        validateTarget(normalizedTargetType, targetId);
        String normalizedSort = normalizeSort(sort);

        LambdaQueryWrapper<Comment> query = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getTargetType, normalizedTargetType)
                .eq(Comment::getTargetId, targetId)
                .isNull(Comment::getParentId)
                .and(visible -> visible
                        .eq(Comment::getStatus, STATUS_VISIBLE)
                        .or(deleted -> deleted
                                .eq(Comment::getStatus, STATUS_DELETED)
                                .gt(Comment::getReplyCount, 0)))
                .orderByDesc(Comment::getIsPinned);
        if ("HOT".equals(normalizedSort)) {
            query.orderByDesc(Comment::getLikeCount)
                    .orderByDesc(Comment::getReplyCount);
        }
        query.orderByDesc(Comment::getCreatedAt).orderByDesc(Comment::getId);

        Page<Comment> commentPage = page(new Page<>(page, size), query);
        return new PageResult<>(
                toResponses(commentPage.getRecords(), viewerUserId),
                commentPage.getCurrent(),
                commentPage.getSize(),
                commentPage.getTotal()
        );
    }

    @Override
    public PageResult<CommentResponse> listReplies(Long commentId, long page, long size, Long viewerUserId) {
        Comment parent = requireComment(commentId);
        if (parent.getParentId() != null || parent.getStatus() == STATUS_HIDDEN) {
            throw notFound();
        }
        validateTarget(parent.getTargetType(), parent.getTargetId());

        Page<Comment> replyPage = page(
                new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getParentId, parent.getId())
                        .eq(Comment::getStatus, STATUS_VISIBLE)
                        .orderByAsc(Comment::getCreatedAt)
                        .orderByAsc(Comment::getId)
        );
        return new PageResult<>(
                toResponses(replyPage.getRecords(), viewerUserId),
                replyPage.getCurrent(),
                replyPage.getSize(),
                replyPage.getTotal()
        );
    }

    @Override
    @Transactional
    public CommentResponse createComment(CommentRequest request, Long userId) {
        String targetType = normalizeTargetType(request.targetType());
        validateTarget(targetType, request.targetId());
        String content = normalizeContent(request.content());
        enforceCommentRateLimit(userId);

        Comment parent = null;
        Long replyToUserId = null;
        if (request.parentId() != null) {
            parent = requireComment(request.parentId());
            if (parent.getParentId() != null
                    || parent.getStatus() != STATUS_VISIBLE
                    || !Objects.equals(parent.getTargetType(), targetType)
                    || !Objects.equals(parent.getTargetId(), request.targetId())) {
                throw parameterError("回复的评论不存在或不属于当前内容");
            }
            replyToUserId = request.replyToUserId() == null ? parent.getUserId() : request.replyToUserId();
            validateReplyTarget(parent, replyToUserId);
        } else if (request.replyToUserId() != null) {
            throw parameterError("回复对象必须与父评论一起提交");
        }

        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setTargetType(targetType);
        comment.setTargetId(request.targetId());
        comment.setContent(content);
        comment.setStatus(STATUS_VISIBLE);
        comment.setParentId(parent == null ? null : parent.getId());
        comment.setReplyToUserId(replyToUserId);
        comment.setLikeCount(0);
        comment.setReplyCount(0);
        comment.setIsPinned(false);
        save(comment);
        if (parent != null) {
            baseMapper.incrementReplyCount(parent.getId());
        }
        return toResponse(getById(comment.getId()), userId, Map.of(), Set.of());
    }

    @Override
    @Transactional
    public void deleteComment(Long id, Long userId) {
        Comment comment = requireComment(id);
        if (comment.getStatus() != STATUS_VISIBLE) {
            throw notFound();
        }
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论", HttpStatus.FORBIDDEN);
        }
        comment.setStatus(STATUS_DELETED);
        comment.setDeletedAt(LocalDateTime.now());
        comment.setIsPinned(false);
        updateById(comment);
        if (comment.getParentId() != null) {
            baseMapper.decrementReplyCount(comment.getParentId());
        }
    }

    @Override
    @Transactional
    public CommentResponse likeComment(Long id, Long userId) {
        Comment comment = requireVisibleComment(id);
        long exists = commentLikeMapper.selectCount(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getCommentId, id)
                .eq(CommentLike::getUserId, userId));
        if (exists == 0) {
            CommentLike like = new CommentLike();
            like.setCommentId(id);
            like.setUserId(userId);
            commentLikeMapper.insert(like);
            baseMapper.incrementLikeCount(id);
        }
        return toResponse(getById(comment.getId()), userId, Map.of(), Set.of(id));
    }

    @Override
    @Transactional
    public CommentResponse unlikeComment(Long id, Long userId) {
        requireComment(id);
        int removed = commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getCommentId, id)
                .eq(CommentLike::getUserId, userId));
        if (removed > 0) {
            baseMapper.decrementLikeCount(id);
        }
        return toResponse(getById(id), userId, Map.of(), Set.of());
    }

    @Override
    @Transactional
    public void reportComment(Long id, CommentReportRequest request, Long userId) {
        requireVisibleComment(id);
        String reason = request.reason().trim().toUpperCase(Locale.ROOT);
        if (!REPORT_REASONS.contains(reason)) {
            throw parameterError("举报原因不受支持");
        }
        long todayCount = commentReportMapper.selectCount(new LambdaQueryWrapper<CommentReport>()
                .eq(CommentReport::getUserId, userId)
                .ge(CommentReport::getCreatedAt, LocalDate.now().atStartOfDay()));
        if (todayCount >= MAX_REPORTS_PER_DAY) {
            throw new BusinessException(ErrorCode.CONFLICT, "今天的举报次数已达上限", HttpStatus.TOO_MANY_REQUESTS);
        }
        long duplicate = commentReportMapper.selectCount(new LambdaQueryWrapper<CommentReport>()
                .eq(CommentReport::getCommentId, id)
                .eq(CommentReport::getUserId, userId));
        if (duplicate > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "你已经举报过这条评论", HttpStatus.CONFLICT);
        }
        CommentReport report = new CommentReport();
        report.setCommentId(id);
        report.setUserId(userId);
        report.setReason(reason);
        report.setDetail(normalizeNullableText(request.detail()));
        report.setStatus("OPEN");
        commentReportMapper.insert(report);
    }

    @Override
    public PageResult<CommentReportResponse> listReports(String status, long page, long size) {
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "OPEN";
        if (!Set.of("OPEN", "RESOLVED", "DISMISSED", "ALL").contains(normalizedStatus)) {
            throw parameterError("举报状态不受支持");
        }
        LambdaQueryWrapper<CommentReport> query = new LambdaQueryWrapper<CommentReport>()
                .orderByDesc(CommentReport::getCreatedAt)
                .orderByDesc(CommentReport::getId);
        if (!"ALL".equals(normalizedStatus)) {
            query.eq(CommentReport::getStatus, normalizedStatus);
        }
        Page<CommentReport> reportPage = commentReportMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(
                toReportResponses(reportPage.getRecords()),
                reportPage.getCurrent(),
                reportPage.getSize(),
                reportPage.getTotal()
        );
    }

    @Override
    @Transactional
    public CommentResponse moderateComment(Long id, AdminCommentActionRequest request) {
        Comment comment = requireComment(id);
        int previousStatus = comment.getStatus();
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if (!MODERATION_ACTIONS.contains(action)) {
            throw parameterError("评论管理操作不受支持");
        }
        switch (action) {
            case "HIDE" -> {
                comment.setStatus(STATUS_HIDDEN);
                comment.setIsPinned(false);
                if (previousStatus == STATUS_VISIBLE && comment.getParentId() != null) {
                    baseMapper.decrementReplyCount(comment.getParentId());
                }
                resolveReports(id, "RESOLVED");
            }
            case "RESTORE" -> {
                comment.setStatus(STATUS_VISIBLE);
                comment.setDeletedAt(null);
                if (previousStatus != STATUS_VISIBLE && comment.getParentId() != null) {
                    baseMapper.incrementReplyCount(comment.getParentId());
                }
                resolveReports(id, "RESOLVED");
            }
            case "PIN" -> {
                if (comment.getParentId() != null || comment.getStatus() != STATUS_VISIBLE) {
                    throw parameterError("只能置顶可见的一级评论");
                }
                comment.setIsPinned(true);
            }
            case "UNPIN" -> comment.setIsPinned(false);
            default -> throw parameterError("评论管理操作不受支持");
        }
        updateById(comment);
        return toResponse(getById(id), null, Map.of(), Set.of());
    }

    private void enforceCommentRateLimit(Long userId) {
        long recentCount = count(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getUserId, userId)
                .ge(Comment::getCreatedAt, LocalDateTime.now().minusMinutes(1)));
        if (recentCount >= MAX_COMMENTS_PER_MINUTE) {
            throw new BusinessException(ErrorCode.CONFLICT, "发布太频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private void validateReplyTarget(Comment parent, Long replyToUserId) {
        if (Objects.equals(parent.getUserId(), replyToUserId)) {
            return;
        }
        long replyExists = count(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getParentId, parent.getId())
                .eq(Comment::getUserId, replyToUserId)
                .eq(Comment::getStatus, STATUS_VISIBLE));
        if (replyExists == 0) {
            throw parameterError("回复对象不在当前评论线程中");
        }
    }

    private List<CommentResponse> toResponses(List<Comment> comments, Long viewerUserId) {
        if (comments.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = comments.stream()
                .flatMap(comment -> java.util.stream.Stream.of(comment.getUserId(), comment.getReplyToUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> usersById = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> likedIds = viewerUserId == null
                ? Set.of()
                : commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                                .eq(CommentLike::getUserId, viewerUserId)
                                .in(CommentLike::getCommentId, comments.stream().map(Comment::getId).toList()))
                        .stream()
                        .map(CommentLike::getCommentId)
                        .collect(Collectors.toSet());
        return comments.stream()
                .map(comment -> toResponse(comment, viewerUserId, usersById, likedIds))
                .toList();
    }

    private CommentResponse toResponse(
            Comment comment,
            Long viewerUserId,
            Map<Long, User> usersById,
            Set<Long> likedIds
    ) {
        User author = usersById.get(comment.getUserId());
        if (author == null) {
            author = userMapper.selectById(comment.getUserId());
        }
        User replyTarget = comment.getReplyToUserId() == null
                ? null
                : usersById.get(comment.getReplyToUserId());
        if (replyTarget == null && comment.getReplyToUserId() != null) {
            replyTarget = userMapper.selectById(comment.getReplyToUserId());
        }
        boolean deleted = Objects.equals(comment.getStatus(), STATUS_DELETED);
        return new CommentResponse(
                comment.getId(),
                comment.getUserId(),
                comment.getTargetType(),
                comment.getTargetId(),
                deleted ? "该评论已删除" : comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getParentId(),
                comment.getReplyToUserId(),
                replyTarget == null ? null : replyTarget.getNickname(),
                author == null ? "已注销用户" : author.getNickname(),
                author == null ? null : author.getAvatarUrl(),
                nullSafe(comment.getLikeCount()),
                nullSafe(comment.getReplyCount()),
                likedIds.contains(comment.getId()),
                Boolean.TRUE.equals(comment.getIsPinned()),
                deleted,
                viewerUserId != null && Objects.equals(comment.getUserId(), viewerUserId)
        );
    }

    private List<CommentReportResponse> toReportResponses(List<CommentReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        Map<Long, User> usersById = userMapper.selectBatchIds(
                        reports.stream().map(CommentReport::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Comment> commentsById = listByIds(
                        reports.stream().map(CommentReport::getCommentId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Comment::getId, Function.identity()));
        return reports.stream().map(report -> {
            User reporter = usersById.get(report.getUserId());
            Comment comment = commentsById.get(report.getCommentId());
            return new CommentReportResponse(
                    report.getId(),
                    report.getCommentId(),
                    report.getUserId(),
                    reporter == null ? "已注销用户" : reporter.getNickname(),
                    report.getReason(),
                    report.getDetail(),
                    report.getStatus(),
                    comment == null ? null : comment.getContent(),
                    comment == null ? null : comment.getStatus(),
                    report.getCreatedAt(),
                    report.getUpdatedAt()
            );
        }).toList();
    }

    private void resolveReports(Long commentId, String status) {
        List<CommentReport> reports = commentReportMapper.selectList(new LambdaQueryWrapper<CommentReport>()
                .eq(CommentReport::getCommentId, commentId)
                .eq(CommentReport::getStatus, "OPEN"));
        for (CommentReport report : reports) {
            report.setStatus(status);
            commentReportMapper.updateById(report);
        }
    }

    private Comment requireVisibleComment(Long id) {
        Comment comment = requireComment(id);
        if (comment.getStatus() != STATUS_VISIBLE) {
            throw notFound();
        }
        validateTarget(comment.getTargetType(), comment.getTargetId());
        return comment;
    }

    private Comment requireComment(Long id) {
        Comment comment = getById(id);
        if (comment == null) {
            throw notFound();
        }
        return comment;
    }

    private String normalizeTargetType(String targetType) {
        if (!StringUtils.hasText(targetType)) {
            throw parameterError("评论目标类型不能为空");
        }
        String normalized = targetType.trim().toUpperCase(Locale.ROOT);
        if (!TARGET_TYPE_SONG.equals(normalized) && !TARGET_TYPE_PLAYLIST.equals(normalized)) {
            throw parameterError("目标类型仅支持 SONG 或 PLAYLIST");
        }
        return normalized;
    }

    private String normalizeSort(String sort) {
        String normalized = StringUtils.hasText(sort) ? sort.trim().toUpperCase(Locale.ROOT) : "LATEST";
        if (!Set.of("LATEST", "HOT").contains(normalized)) {
            throw parameterError("评论排序仅支持 LATEST 或 HOT");
        }
        return normalized;
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (!StringUtils.hasText(normalized)) {
            throw parameterError("评论内容不能为空");
        }
        if (normalized.codePointCount(0, normalized.length()) > 500) {
            throw parameterError("评论内容不能超过 500 字");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.strip();
    }

    private void validateTarget(String targetType, Long targetId) {
        if (targetId == null || targetId <= 0) {
            throw parameterError("评论目标无效");
        }
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

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "评论不存在", HttpStatus.NOT_FOUND);
    }

    private BusinessException parameterError(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message, HttpStatus.BAD_REQUEST);
    }
}
