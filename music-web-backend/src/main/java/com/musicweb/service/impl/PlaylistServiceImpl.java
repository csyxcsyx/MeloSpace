package com.musicweb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.entity.Playlist;
import com.musicweb.mapper.PlaylistMapper;
import com.musicweb.service.PlaylistService;
import org.springframework.stereotype.Service;

@Service
public class PlaylistServiceImpl extends ServiceImpl<PlaylistMapper, Playlist> implements PlaylistService {
}
