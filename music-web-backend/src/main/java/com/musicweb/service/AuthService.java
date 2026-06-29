package com.musicweb.service;

import com.musicweb.dto.LoginRequest;
import com.musicweb.dto.RegisterRequest;
import com.musicweb.vo.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
