package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.dto.ArtistUpsertRequest;
import com.musicweb.entity.Artist;
import com.musicweb.vo.ArtistResponse;

public interface ArtistService extends IService<Artist> {

    ArtistResponse createArtist(ArtistUpsertRequest request);

    ArtistResponse updateArtist(Long id, ArtistUpsertRequest request);
}
