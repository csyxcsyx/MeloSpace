package com.musicweb.controller;

import com.musicweb.common.ApiResponse;
import com.musicweb.service.CommentService;
import com.musicweb.service.PlaylistService;
import com.musicweb.service.SongService;
import com.musicweb.service.UserService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final UserService userService;
    private final SongService songService;
    private final PlaylistService playlistService;
    private final CommentService commentService;

    public AdminDashboardController(
            UserService userService,
            SongService songService,
            PlaylistService playlistService,
            CommentService commentService
    ) {
        this.userService = userService;
        this.songService = songService;
        this.playlistService = playlistService;
        this.commentService = commentService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Long>> dashboard() {
        return ApiResponse.ok(Map.of(
                "users", userService.count(),
                "songs", songService.count(),
                "playlists", playlistService.count(),
                "comments", commentService.count()
        ));
    }
}
