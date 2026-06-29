package com.musicweb.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.entity.User;
import com.musicweb.security.JwtAuthenticationFilter;
import com.musicweb.security.RestAccessDeniedHandler;
import com.musicweb.security.RestAuthenticationEntryPoint;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/api/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/me/**", "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/playlists", "/api/playlists/*/songs").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/favorites", "/api/comments", "/api/songs/*/play-record").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/playlists/*", "/api/playlists/*/songs/order").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/playlists/*", "/api/playlists/*/songs/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/favorites", "/api/comments/*").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserService userService) {
        return username -> {
            User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username), false);
            if (user == null) {
                throw new UsernameNotFoundException("User not found: " + username);
            }
            return new UserPrincipal(user);
        };
    }
}
