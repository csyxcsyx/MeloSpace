package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.dto.PlayRecordRequest;
import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.PlayHistory;
import com.musicweb.entity.Song;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.PlayHistoryMapper;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import com.musicweb.service.PlayHistoryService;
import com.musicweb.service.SongService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.PlayHistoryResponse;
import com.musicweb.vo.SongResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayHistoryServiceImpl extends ServiceImpl<PlayHistoryMapper, PlayHistory> implements PlayHistoryService {

    private static final int STATUS_PUBLISHED = 1;

    private final SongService songService;
    private final ArtistService artistService;
    private final AlbumService albumService;

    public PlayHistoryServiceImpl(SongService songService, ArtistService artistService, AlbumService albumService) {
        this.songService = songService;
        this.artistService = artistService;
        this.albumService = albumService;
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
                playHistory.getPlayedAt(),
                toSongResponse(playHistory.getSongId())
        );
    }

    private SongResponse toSongResponse(Long songId) {
        Song song = songService.getById(songId);
        if (song == null) {
            return null;
        }

        Map<Long, Artist> artistsById = new HashMap<>();
        if (song.getArtistId() != null) {
            Artist artist = artistService.getById(song.getArtistId());
            if (artist != null) {
                artistsById.put(artist.getId(), artist);
            }
        }

        Map<Long, Album> albumsById = new HashMap<>();
        if (song.getAlbumId() != null) {
            Album album = albumService.getById(song.getAlbumId());
            if (album != null) {
                albumsById.put(album.getId(), album);
            }
        }

        return MusicResponseAssembler.toSongResponse(song, artistsById, albumsById);
    }
}
