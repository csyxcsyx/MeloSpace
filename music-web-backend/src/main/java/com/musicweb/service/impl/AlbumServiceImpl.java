package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.dto.AlbumUpsertRequest;
import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.Song;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.AlbumMapper;
import com.musicweb.mapper.SongMapper;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.AlbumResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AlbumServiceImpl extends ServiceImpl<AlbumMapper, Album> implements AlbumService {

    private final ArtistService artistService;
    private final SongMapper songMapper;

    public AlbumServiceImpl(ArtistService artistService, SongMapper songMapper) {
        this.artistService = artistService;
        this.songMapper = songMapper;
    }

    @Override
    public AlbumResponse createAlbum(AlbumUpsertRequest request) {
        Artist artist = getExistingArtist(request.artistId());
        Album album = new Album();
        applyAlbumRequest(album, request);
        save(album);
        return MusicResponseAssembler.toAlbumResponse(getById(album.getId()), Map.of(artist.getId(), artist));
    }

    @Override
    public AlbumResponse updateAlbum(Long id, AlbumUpsertRequest request) {
        Album album = getById(id);
        if (album == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "专辑不存在", HttpStatus.NOT_FOUND);
        }
        Artist artist = getExistingArtist(request.artistId());
        applyAlbumRequest(album, request);
        updateById(album);
        return MusicResponseAssembler.toAlbumResponse(getById(id), Map.of(artist.getId(), artist));
    }

    @Override
    public void deleteAlbum(Long id) {
        Album album = getById(id);
        if (album == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "专辑不存在", HttpStatus.NOT_FOUND);
        }
        Long songCount = songMapper.selectCount(new LambdaQueryWrapper<Song>().eq(Song::getAlbumId, id));
        if (songCount != null && songCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "专辑下仍有歌曲，无法删除", HttpStatus.CONFLICT);
        }
        removeById(id);
    }

    private Artist getExistingArtist(Long artistId) {
        Artist artist = artistService.getById(artistId);
        if (artist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌手不存在", HttpStatus.NOT_FOUND);
        }
        return artist;
    }

    private void applyAlbumRequest(Album album, AlbumUpsertRequest request) {
        album.setTitle(request.title());
        album.setArtistId(request.artistId());
        album.setCoverUrl(request.coverUrl());
        album.setReleaseDate(request.releaseDate());
    }
}
