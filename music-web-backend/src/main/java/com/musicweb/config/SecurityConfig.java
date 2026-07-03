package com.musicweb.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.entity.User;
import com.musicweb.security.JwtAuthenticationFilter;
import com.musicweb.security.RestAccessDeniedHandler;
import com.musicweb.security.RestAuthenticationEntryPoint;
import com.musicweb.security.UserPrincipal;
import com.musicweb.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
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
import org.springframework.web.filter.OncePerRequestFilter;

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
                .headers(headers -> headers.cacheControl(cache -> cache.disable()))
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
    public FilterRegistrationBean<OncePerRequestFilter> apiNoStoreCacheHeaderFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getRequestURI();
                return !(path.startsWith("/api/") || path.startsWith("/actuator/"));
            }

            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Pragma", "no-cache");
                response.setDateHeader("Expires", 0);
                filterChain.doFilter(request, response);
            }
        });
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
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
