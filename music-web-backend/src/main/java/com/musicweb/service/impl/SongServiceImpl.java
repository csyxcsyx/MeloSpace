package com.musicweb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.entity.Song;
import com.musicweb.mapper.SongMapper;
import com.musicweb.service.SongService;
import org.springframework.stereotype.Service;

@Service
public class SongServiceImpl extends ServiceImpl<SongMapper, Song> implements SongService {
}
