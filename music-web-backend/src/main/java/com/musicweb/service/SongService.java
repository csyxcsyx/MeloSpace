package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.common.PageResult;
import com.musicweb.dto.SongStatusRequest;
import com.musicweb.dto.SongUpsertRequest;
import com.musicweb.entity.Song;
import com.musicweb.vo.SearchResponse;
import com.musicweb.vo.SongResponse;

public interface SongService extends IService<Song> {

    PageResult<SongResponse> listPublishedSongs(long page, long size, String keyword, Long artistId, Long albumId, String sort);

    SongResponse getPublishedSong(Long id);

    SearchResponse search(String keyword);

    PageResult<SongResponse> listAdminSongs(long page, long size, String keyword, Integer status);

    SongResponse createSong(SongUpsertRequest request);

    SongResponse updateSong(Long id, SongUpsertRequest request);

    SongResponse updateSongStatus(Long id, SongStatusRequest request);

    void deleteSong(Long id);
}
