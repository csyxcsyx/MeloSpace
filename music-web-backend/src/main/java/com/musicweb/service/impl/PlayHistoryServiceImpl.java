package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.dto.PlayRecordRequest;
import com.musicweb.entity.PlayHistory;
import com.musicweb.entity.Song;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.PlayHistoryMapper;
import com.musicweb.service.PlayHistoryService;
import com.musicweb.service.SongService;
import com.musicweb.vo.PlayHistoryResponse;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayHistoryServiceImpl extends ServiceImpl<PlayHistoryMapper, PlayHistory> implements PlayHistoryService {

    private static final int STATUS_PUBLISHED = 1;

    private final SongService songService;

    public PlayHistoryServiceImpl(SongService songService) {
        this.songService = songService;
    }

    @Override
    @Transactional
    public PlayHistoryResponse recordSongPlay(Long songId, PlayRecordRequest request, Long userId) {
        Song song = songService.getById(songId);
        if (song == null || !Objects.equals(song.getStatus(), STATUS_PUBLISHED)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌曲不存在或已下架", HttpStatus.NOT_FOUND);
        }

        PlayHistory playHistory = new PlayHistory();
        playHistory.setUserId(userId);
        playHistory.setSongId(songId);
        playHistory.setProgressSeconds(request.progressSeconds() == null ? 0 : request.progressSeconds());
        playHistory.setSourceType(request.sourceType());
        playHistory.setPlayedAt(LocalDateTime.now());
        save(playHistory);

        song.setPlayCount(song.getPlayCount() == null ? 1 : song.getPlayCount() + 1);
        songService.updateById(song);

        return toResponse(getById(playHistory.getId()));
    }

    @Override
    public PageResult<PlayHistoryResponse> listRecentPlays(Long userId, long page, long size) {
        Page<PlayHistory> historyPage = page(
                new Page<>(page, size),
                new LambdaQueryWrapper<PlayHistory>()
                        .eq(PlayHistory::getUserId, userId)
                        .orderByDesc(PlayHistory::getPlayedAt)
                        .orderByDesc(PlayHistory::getId)
        );
        return new PageResult<>(
                historyPage.getRecords().stream().map(this::toResponse).toList(),
                historyPage.getCurrent(),
                historyPage.getSize(),
                historyPage.getTotal()
        );
    }

    private PlayHistoryResponse toResponse(PlayHistory playHistory) {
        return new PlayHistoryResponse(
                playHistory.getId(),
                playHistory.getUserId(),
                playHistory.getSongId(),
                playHistory.getProgressSeconds(),
                playHistory.getSourceType(),
                playHistory.getPlayedAt()
        );
    }
}
