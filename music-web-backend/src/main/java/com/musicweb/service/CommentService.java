package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.common.PageResult;
import com.musicweb.dto.AdminCommentActionRequest;
import com.musicweb.dto.CommentReportRequest;
import com.musicweb.dto.CommentRequest;
import com.musicweb.entity.Comment;
import com.musicweb.vo.CommentReportResponse;
import com.musicweb.vo.CommentResponse;

public interface CommentService extends IService<Comment> {

    PageResult<CommentResponse> listComments(
            String targetType,
            Long targetId,
            String sort,
            long page,
            long size,
            Long viewerUserId
    );

    PageResult<CommentResponse> listReplies(Long commentId, long page, long size, Long viewerUserId);

    CommentResponse createComment(CommentRequest request, Long userId);

    void deleteComment(Long id, Long userId);

    CommentResponse likeComment(Long id, Long userId);

    CommentResponse unlikeComment(Long id, Long userId);

    void reportComment(Long id, CommentReportRequest request, Long userId);

    PageResult<CommentReportResponse> listReports(String status, long page, long size);

    CommentResponse moderateComment(Long id, AdminCommentActionRequest request);
}
