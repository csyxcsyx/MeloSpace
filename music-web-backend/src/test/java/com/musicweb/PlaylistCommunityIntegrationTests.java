package com.musicweb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:music_web_playlists;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
class PlaylistCommunityIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetPlaylistData() {
        jdbcTemplate.update("DELETE FROM comment_report");
        jdbcTemplate.update("DELETE FROM comment_like");
        jdbcTemplate.update("DELETE FROM comment");
        jdbcTemplate.update("DELETE FROM playlist_tag");
        jdbcTemplate.update("DELETE FROM playlist_song");
        jdbcTemplate.update("DELETE FROM favorite");
        jdbcTemplate.update("DELETE FROM playlist");
    }

    @Test
    void ownersCanEditTagsBatchSongsAndRecordPublicPlays() {
        String ownerToken = login("demo", "User@123456");
        JsonNode created = exchange(
                "/api/playlists",
                HttpMethod.POST,
                ownerToken,
                Map.of(
                        "title", "夜间精选",
                        "description", "适合夜晚",
                        "visibility", "PUBLIC",
                        "tags", List.of("治愈", "华语")
                )
        ).getBody().get("data");
        long playlistId = created.get("id").asLong();
        assertThat(created.get("creatorNickname").asText()).isEqualTo("演示用户");
        assertThat(created.get("canManage").asBoolean()).isTrue();
        assertThat(created.get("tags")).hasSize(2);

        ResponseEntity<JsonNode> batchAdded = exchange(
                "/api/playlists/" + playlistId + "/songs/batch",
                HttpMethod.POST,
                ownerToken,
                Map.of("songIds", List.of(1L))
        );
        assertThat(batchAdded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(batchAdded.getBody().get("data").get("songs")).hasSize(1);

        ResponseEntity<JsonNode> publicPlay = restTemplate.postForEntity(
                url("/api/playlists/" + playlistId + "/play"),
                null,
                JsonNode.class
        );
        assertThat(publicPlay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicPlay.getBody().get("data").get("playCount").asLong()).isEqualTo(1);

        String otherToken = login("admin", "Admin@123456");
        ResponseEntity<JsonNode> forbidden = exchange(
                "/api/playlists/" + playlistId + "/songs/batch",
                HttpMethod.DELETE,
                otherToken,
                Map.of("songIds", List.of(1L))
        );
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> removed = exchange(
                "/api/playlists/" + playlistId + "/songs/batch",
                HttpMethod.DELETE,
                ownerToken,
                Map.of("songIds", List.of(1L))
        );
        assertThat(removed.getBody().get("data").get("songs")).isEmpty();
    }

    @Test
    void favoritesUpdateCountersStatusesAndHydratedLists() {
        String ownerToken = login("demo", "User@123456");
        long playlistId = createPlaylist(ownerToken, "收藏计数歌单");
        String fanToken = login("admin", "Admin@123456");

        ResponseEntity<JsonNode> favorite = exchange(
                "/api/favorites",
                HttpMethod.POST,
                fanToken,
                Map.of("targetType", "PLAYLIST", "targetId", playlistId)
        );
        assertThat(favorite.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(favorite.getBody().get("data").get("playlist").get("title").asText()).isEqualTo("收藏计数歌单");

        exchange(
                "/api/favorites",
                HttpMethod.POST,
                fanToken,
                Map.of("targetType", "PLAYLIST", "targetId", playlistId)
        );
        JsonNode detail = restTemplate.getForEntity(
                url("/api/playlists/" + playlistId),
                JsonNode.class
        ).getBody().get("data");
        assertThat(detail.get("favoriteCount").asLong()).isEqualTo(1);

        ResponseEntity<JsonNode> statuses = exchange(
                "/api/favorites/status?targetType=PLAYLIST&targetIds=" + playlistId + ",999",
                HttpMethod.GET,
                fanToken,
                null
        );
        assertThat(statuses.getBody().get("data").get(String.valueOf(playlistId)).asBoolean()).isTrue();
        assertThat(statuses.getBody().get("data").get("999").asBoolean()).isFalse();

        ResponseEntity<JsonNode> favoritesPage = exchange(
                "/api/users/me/favorites?page=1&size=20",
                HttpMethod.GET,
                fanToken,
                null
        );
        JsonNode favoriteItem = favoritesPage.getBody().get("data").get("items").get(0);
        assertThat(favoriteItem.get("playlist").get("id").asLong()).isEqualTo(playlistId);

        exchange(
                "/api/favorites?targetType=PLAYLIST&targetId=" + playlistId,
                HttpMethod.DELETE,
                fanToken,
                null
        );
        JsonNode afterRemoval = restTemplate.getForEntity(
                url("/api/playlists/" + playlistId),
                JsonNode.class
        ).getBody().get("data");
        assertThat(afterRemoval.get("favoriteCount").asLong()).isZero();
    }

    @Test
    void makingPlaylistPrivateRemovesExternalFavoritesAndPublicComments() {
        String ownerToken = login("demo", "User@123456");
        long playlistId = createPlaylist(ownerToken, "即将私有");
        String fanToken = login("admin", "Admin@123456");
        exchange(
                "/api/favorites",
                HttpMethod.POST,
                fanToken,
                Map.of("targetType", "PLAYLIST", "targetId", playlistId)
        );
        exchange(
                "/api/comments",
                HttpMethod.POST,
                fanToken,
                Map.of("targetType", "PLAYLIST", "targetId", playlistId, "content", "公开时的评论")
        );

        ResponseEntity<JsonNode> updated = exchange(
                "/api/playlists/" + playlistId,
                HttpMethod.PUT,
                ownerToken,
                Map.of(
                        "title", "现在私有",
                        "description", "评论仍保留",
                        "visibility", "PRIVATE",
                        "tags", List.of("私藏")
                )
        );
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("data").get("favoriteCount").asLong()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorite WHERE target_type='PLAYLIST' AND target_id=?",
                Long.class,
                playlistId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comment WHERE target_type='PLAYLIST' AND target_id=?",
                Long.class,
                playlistId
        )).isEqualTo(1);

        ResponseEntity<JsonNode> outsiderDetail = exchange(
                "/api/playlists/" + playlistId,
                HttpMethod.GET,
                fanToken,
                null
        );
        assertThat(outsiderDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<JsonNode> publicComments = restTemplate.getForEntity(
                url("/api/comments?targetType=PLAYLIST&targetId=" + playlistId),
                JsonNode.class
        );
        assertThat(publicComments.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private long createPlaylist(String token, String title) {
        return exchange(
                "/api/playlists",
                HttpMethod.POST,
                token,
                Map.of("title", title, "visibility", "PUBLIC", "tags", List.of("测试"))
        ).getBody().get("data").get("id").asLong();
    }

    private String login(String username, String password) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url("/api/auth/login"),
                Map.of("username", username, "password", password),
                JsonNode.class
        );
        return response.getBody().get("data").get("token").asText();
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
