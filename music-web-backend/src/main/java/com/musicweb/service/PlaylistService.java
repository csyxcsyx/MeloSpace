package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.common.PageResult;
import com.musicweb.dto.PlaylistOrderRequest;
import com.musicweb.dto.PlaylistSongRequest;
import com.musicweb.dto.PlaylistUpsertRequest;
import com.musicweb.entity.Playlist;
import com.musicweb.vo.PlaylistDetailResponse;
import com.musicweb.vo.PlaylistResponse;

public interface PlaylistService extends IService<Playlist> {

    PageResult<PlaylistResponse> listPublicPlaylists(long page, long size, String keyword);

    PageResult<PlaylistResponse> listUserPlaylists(Long userId, long page, long size);

    PlaylistDetailResponse getPlaylist(Long id, Long currentUserId);

    PlaylistDetailResponse createPlaylist(PlaylistUpsertRequest request, Long userId);

    PlaylistDetailResponse updatePlaylist(Long id, PlaylistUpsertRequest request, Long userId);

    void deletePlaylist(Long id, Long userId);

    PlaylistDetailResponse addSong(Long playlistId, PlaylistSongRequest request, Long userId);

    PlaylistDetailResponse removeSong(Long playlistId, Long songId, Long userId);

    PlaylistDetailResponse reorderSongs(Long playlistId, PlaylistOrderRequest request, Long userId);
}
