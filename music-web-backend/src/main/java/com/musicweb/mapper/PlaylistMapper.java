package com.musicweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicweb.entity.Playlist;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PlaylistMapper extends BaseMapper<Playlist> {

    @Update("UPDATE playlist SET play_count = play_count + 1 WHERE id = #{id}")
    int incrementPlayCount(@Param("id") Long id);

    @Update("UPDATE playlist SET favorite_count = favorite_count + 1 WHERE id = #{id}")
    int incrementFavoriteCount(@Param("id") Long id);

    @Update("UPDATE playlist SET favorite_count = CASE WHEN favorite_count > 0 THEN favorite_count - 1 ELSE 0 END WHERE id = #{id}")
    int decrementFavoriteCount(@Param("id") Long id);

    @Update("UPDATE playlist SET favorite_count = #{count} WHERE id = #{id}")
    int setFavoriteCount(@Param("id") Long id, @Param("count") long count);
}
