package com.musicweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicweb.entity.Comment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CommentMapper extends BaseMapper<Comment> {

    @Update("UPDATE comment SET like_count = like_count + 1 WHERE id = #{id}")
    int incrementLikeCount(@Param("id") Long id);

    @Update("UPDATE comment SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END WHERE id = #{id}")
    int decrementLikeCount(@Param("id") Long id);

    @Update("UPDATE comment SET reply_count = reply_count + 1 WHERE id = #{id}")
    int incrementReplyCount(@Param("id") Long id);

    @Update("UPDATE comment SET reply_count = CASE WHEN reply_count > 0 THEN reply_count - 1 ELSE 0 END WHERE id = #{id}")
    int decrementReplyCount(@Param("id") Long id);
}
