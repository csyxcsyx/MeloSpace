package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.dto.FavoriteRequest;
import com.musicweb.entity.Favorite;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.Song;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.FavoriteMapper;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.service.FavoriteService;
import com.musicweb.service.SongService;
import com.musicweb.vo.FavoriteResponse;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    private static final String TARGET_TYPE_SONG = "SONG";
    private static final String TARGET_TYPE_PLAYLIST = "PLAYLIST";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final int STATUS_PUBLISHED = 1;

    private final SongService songService;
    private final PlaylistMapper playlistMapper;

    public FavoriteServiceImpl(SongService songService, PlaylistMapper playlistMapper) {
        this.songService = songService;
        this.playlistMapper = playlistMapper;
    }

    @Override
    @Transactional
    public FavoriteResponse favorite(FavoriteRequest request, Long userId) {
        String targetType = normalizeTargetType(request.targetType());
        validateTarget(targetType, request.targetId(), userId);

        Favorite existing = getOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .eq(Favorite::getTargetId, request.targetId()), false);
        if (existing != null) {
            return toResponse(existing);
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetType(targetType);
        favorite.setTargetId(request.targetId());
        save(favorite);
        return toResponse(getById(favorite.getId()));
    }

    @Override
    @Transactional
    public void unfavorite(String targetType, Long targetId, Long userId) {
        String normalizedTargetType = normalizeTargetType(targetType);
        remove(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, normalizedTargetType)
                .eq(Favorite::getTargetId, targetId));
    }

    @Override
    public PageResult<FavoriteResponse> listUserFavorites(Long userId, long page, long size) {
        Page<Favorite> favoritePage = page(
                new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreatedAt)
                        .orderByDesc(Favorite::getId)
        );
        return new PageResult<>(
                favoritePage.getRecords().stream().map(this::toResponse).toList(),
                favoritePage.getCurrent(),
                favoritePage.getSize(),
                favoritePage.getTotal()
        );
    }

    private String normalizeTargetType(String targetType) {
        if (!StringUtils.hasText(targetType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "收藏目标类型不能为空", HttpStatus.BAD_REQUEST);
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

    private FavoriteResponse toResponse(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getUserId(),
                favorite.getTargetType(),
                favorite.getTargetId(),
                favorite.getCreatedAt()
        );
    }
}
