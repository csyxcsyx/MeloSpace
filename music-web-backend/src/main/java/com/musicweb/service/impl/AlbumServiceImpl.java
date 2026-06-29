package com.musicweb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.entity.Album;
import com.musicweb.mapper.AlbumMapper;
import com.musicweb.service.AlbumService;
import org.springframework.stereotype.Service;

@Service
public class AlbumServiceImpl extends ServiceImpl<AlbumMapper, Album> implements AlbumService {
}
