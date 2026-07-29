package com.musicweb.service;

import com.musicweb.vo.CommunityDiscoverResponse;

public interface DiscoverService {

    CommunityDiscoverResponse community(Long currentUserId);
}
