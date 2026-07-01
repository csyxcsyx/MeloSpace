package com.musicweb.service;

import com.musicweb.entity.User;

public interface AdminAccountService {

    boolean isBootstrapAdminCredentials(String username, String password);

    User ensureAdminAccount();
}
