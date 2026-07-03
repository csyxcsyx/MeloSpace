package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.dto.ArtistUpsertRequest;
import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.Song;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.AlbumMapper;
import com.musicweb.mapper.ArtistMapper;
import com.musicweb.mapper.SongMapper;
import com.musicweb.service.ArtistService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.ArtistResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ArtistServiceImpl extends ServiceImpl<ArtistMapper, Artist> implements ArtistService {

    private final AlbumMapper albumMapper;
    private final SongMapper songMapper;

    public ArtistServiceImpl(AlbumMapper albumMapper, SongMapper songMapper) {
        this.albumMapper = albumMapper;
        this.songMapper = songMapper;
    }

    @Override
    public ArtistResponse createArtist(ArtistUpsertRequest request) {
        Artist artist = new Artist();
        applyArtistRequest(artist, request);
        save(artist);
        return MusicResponseAssembler.toArtistResponse(getById(artist.getId()));
    }

    @Override
    public ArtistResponse updateArtist(Long id, ArtistUpsertRequest request) {
        Artist artist = getById(id);
        if (artist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌手不存在", HttpStatus.NOT_FOUND);
        }
        applyArtistRequest(artist, request);
        updateById(artist);
        return MusicResponseAssembler.toArtistResponse(getById(id));
    }

    @Override
    public void deleteArtist(Long id) {
        Artist artist = getById(id);
        if (artist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "歌手不存在", HttpStatus.NOT_FOUND);
        }
        Long albumCount = albumMapper.selectCount(new LambdaQueryWrapper<Album>().eq(Album::getArtistId, id));
        Long songCount = songMapper.selectCount(new LambdaQueryWrapper<Song>().eq(Song::getArtistId, id));
        if ((albumCount != null && albumCount > 0) || (songCount != null && songCount > 0)) {
            throw new BusinessException(ErrorCode.CONFLICT, "歌手下仍有专辑或歌曲，无法删除", HttpStatus.CONFLICT);
        }
        removeById(id);
    }

    private void applyArtistRequest(Artist artist, ArtistUpsertRequest request) {
        artist.setName(request.name());
        artist.setBio(request.bio());
        artist.setAvatarUrl(request.avatarUrl());
    }
}
