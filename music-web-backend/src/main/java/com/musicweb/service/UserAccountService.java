package com.musicweb.service;

import com.musicweb.common.PageResult;
import com.musicweb.vo.AdminUserResponse;

public interface UserAccountService {

    PageResult<AdminUserResponse> listUsers(long page, long size, String keyword, String role, Integer status);

    void deleteUser(Long userId);
}
