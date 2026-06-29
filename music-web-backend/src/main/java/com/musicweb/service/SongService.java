package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.common.PageResult;
import com.musicweb.entity.Song;
import com.musicweb.vo.SearchResponse;
import com.musicweb.vo.SongResponse;

public interface SongService extends IService<Song> {

    PageResult<SongResponse> listPublishedSongs(long page, long size, String keyword, Long artistId, Long albumId);

    SongResponse getPublishedSong(Long id);

    SearchResponse search(String keyword);
}
