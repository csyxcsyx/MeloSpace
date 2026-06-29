package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.common.PageResult;
import com.musicweb.dto.FavoriteRequest;
import com.musicweb.entity.Favorite;
import com.musicweb.vo.FavoriteResponse;

public interface FavoriteService extends IService<Favorite> {

    FavoriteResponse favorite(FavoriteRequest request, Long userId);

    void unfavorite(String targetType, Long targetId, Long userId);

    PageResult<FavoriteResponse> listUserFavorites(Long userId, long page, long size);
}
