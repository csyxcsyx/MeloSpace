package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.common.PageResult;
import com.musicweb.dto.PlayRecordRequest;
import com.musicweb.entity.PlayHistory;
import com.musicweb.vo.PlayHistoryResponse;

public interface PlayHistoryService extends IService<PlayHistory> {

    PlayHistoryResponse recordSongPlay(Long songId, PlayRecordRequest request, Long userId);

    PageResult<PlayHistoryResponse> listRecentPlays(Long userId, long page, long size);

    void clearRecentPlays(Long userId);
}
