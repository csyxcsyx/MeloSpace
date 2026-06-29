package com.musicweb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.entity.PlayHistory;
import com.musicweb.mapper.PlayHistoryMapper;
import com.musicweb.service.PlayHistoryService;
import org.springframework.stereotype.Service;

@Service
public class PlayHistoryServiceImpl extends ServiceImpl<PlayHistoryMapper, PlayHistory> implements PlayHistoryService {
}
