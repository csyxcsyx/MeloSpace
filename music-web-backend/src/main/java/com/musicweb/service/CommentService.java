package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.common.PageResult;
import com.musicweb.dto.CommentRequest;
import com.musicweb.entity.Comment;
import com.musicweb.vo.CommentResponse;

public interface CommentService extends IService<Comment> {

    PageResult<CommentResponse> listComments(String targetType, Long targetId, long page, long size);

    CommentResponse createComment(CommentRequest request, Long userId);

    void deleteComment(Long id, Long userId);
}
