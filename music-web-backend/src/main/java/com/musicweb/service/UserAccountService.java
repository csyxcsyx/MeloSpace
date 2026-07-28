package com.musicweb.service;

import com.musicweb.common.PageResult;
import com.musicweb.dto.UpdateUserProfileRequest;
import com.musicweb.vo.AdminUserResponse;
import com.musicweb.vo.PlaylistResponse;
import com.musicweb.vo.PublicUserResponse;
import com.musicweb.vo.UserSummaryResponse;

public interface UserAccountService {

    PageResult<AdminUserResponse> listUsers(long page, long size, String keyword, String role, Integer status);

    PublicUserResponse getPublicUser(Long userId);

    PageResult<PlaylistResponse> listPublicUserPlaylists(Long userId, long page, long size);

    UserSummaryResponse getCurrentUser(Long userId);

    UserSummaryResponse updateProfile(Long userId, UpdateUserProfileRequest request);

    void deleteUser(Long userId);
}
