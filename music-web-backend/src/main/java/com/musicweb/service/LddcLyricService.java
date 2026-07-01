package com.musicweb.service;

import com.musicweb.dto.LddcLyricRequest;
import com.musicweb.vo.LddcLyricResponse;

public interface LddcLyricService {

    LddcLyricResponse importLyrics(LddcLyricRequest request);
}
