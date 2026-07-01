package com.musicweb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.musicweb.dto.AlbumUpsertRequest;
import com.musicweb.entity.Album;
import com.musicweb.vo.AlbumResponse;

public interface AlbumService extends IService<Album> {

    AlbumResponse createAlbum(AlbumUpsertRequest request);

    AlbumResponse updateAlbum(Long id, AlbumUpsertRequest request);

    void deleteAlbum(Long id);
}
