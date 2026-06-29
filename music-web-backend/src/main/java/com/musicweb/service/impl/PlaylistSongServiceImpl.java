package com.musicweb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.entity.PlaylistSong;
import com.musicweb.mapper.PlaylistSongMapper;
import com.musicweb.service.PlaylistSongService;
import org.springframework.stereotype.Service;

@Service
public class PlaylistSongServiceImpl extends ServiceImpl<PlaylistSongMapper, PlaylistSong> implements PlaylistSongService {
}
