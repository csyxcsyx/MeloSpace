package com.musicweb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
        "spring.datasource.url=jdbc:h2:mem:music_web_comments;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
class CommentThreadIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetCommunityData() {
        jdbcTemplate.update("DELETE FROM comment_report");
        jdbcTemplate.update("DELETE FROM comment_like");
        jdbcTemplate.update("DELETE FROM comment");
        jdbcTemplate.update("DELETE FROM playlist_song");
        jdbcTemplate.update("DELETE FROM favorite");
        jdbcTemplate.update("DELETE FROM playlist");
        jdbcTemplate.update("""
                INSERT INTO playlist
                  (id, user_id, title, description, visibility, play_count, favorite_count)
                VALUES
                  (101, 2, '公开歌单', '用于评论测试', 'PUBLIC', 0, 0),
                  (102, 2, '私有歌单', '不可评论', 'PRIVATE', 0, 0)
                """);
    }

    @Test
    void publicThreadsExposeProfilesAndHidePrivatePlaylistComments() {
        String token = login("demo", "User@123456");
        ResponseEntity<JsonNode> created = exchange(
                "/api/comments",
                HttpMethod.POST,
                token,
                Map.of("targetType", "PLAYLIST", "targetId", 101, "content", "公开歌单真好听")
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> publicList = restTemplate.getForEntity(
                url("/api/comments?targetType=PLAYLIST&targetId=101&sort=LATEST"),
                JsonNode.class
        );
        assertThat(publicList.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode item = publicList.getBody().get("data").get("items").get(0);
        assertThat(item.get("userNickname").asText()).isEqualTo("演示用户");
        assertThat(item.get("likeCount").asInt()).isZero();
        assertThat(item.get("mine").asBoolean()).isFalse();

        ResponseEntity<JsonNode> privateList = restTemplate.getForEntity(
                url("/api/comments?targetType=PLAYLIST&targetId=102"),
                JsonNode.class
        );
        assertThat(privateList.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> privateCreate = exchange(
                "/api/comments",
                HttpMethod.POST,
                token,
                Map.of("targetType", "PLAYLIST", "targetId", 102, "content", "不应成功")
        );
        assertThat(privateCreate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void repliesLikesAndSoftDeletionKeepThreadConsistent() {
        String token = login("demo", "User@123456");
        JsonNode root = exchange(
                "/api/comments",
                HttpMethod.POST,
                token,
                Map.of("targetType", "SONG", "targetId", 1, "content", "根评论")
        ).getBody().get("data");
        long rootId = root.get("id").asLong();

        JsonNode reply = exchange(
                "/api/comments",
                HttpMethod.POST,
                token,
                Map.of(
                        "targetType", "SONG",
                        "targetId", 1,
                        "content", "一级回复",
                        "parentId", rootId,
                        "replyToUserId", 2
                )
        ).getBody().get("data");
        long replyId = reply.get("id").asLong();

        ResponseEntity<JsonNode> liked = exchange("/api/comments/" + replyId + "/like", HttpMethod.PUT, token, null);
        assertThat(liked.getBody().get("data").get("liked").asBoolean()).isTrue();
        assertThat(liked.getBody().get("data").get("likeCount").asInt()).isEqualTo(1);
        ResponseEntity<JsonNode> likedAgain = exchange("/api/comments/" + replyId + "/like", HttpMethod.PUT, token, null);
        assertThat(likedAgain.getBody().get("data").get("likeCount").asInt()).isEqualTo(1);

        ResponseEntity<JsonNode> publicReplies = restTemplate.getForEntity(
                url("/api/comments/" + rootId + "/replies"),
                JsonNode.class
        );
        assertThat(publicReplies.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicReplies.getBody().get("data").get("total").asLong()).isEqualTo(1);

        ResponseEntity<JsonNode> deletedRoot = exchange("/api/comments/" + rootId, HttpMethod.DELETE, token, null);
        assertThat(deletedRoot.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode visiblePlaceholder = restTemplate.getForEntity(
                url("/api/comments?targetType=SONG&targetId=1"),
                JsonNode.class
        ).getBody().get("data").get("items").get(0);
        assertThat(visiblePlaceholder.get("deleted").asBoolean()).isTrue();
        assertThat(visiblePlaceholder.get("content").asText()).isEqualTo("该评论已删除");
        assertThat(visiblePlaceholder.get("replyCount").asInt()).isEqualTo(1);
    }

    @Test
    void reportsAreUniqueAndAdminsCanHideRestoreAndPin() {
        String userToken = login("demo", "User@123456");
        long commentId = exchange(
                "/api/comments",
                HttpMethod.POST,
                userToken,
                Map.of("targetType", "SONG", "targetId", 1, "content", "等待管理的评论")
        ).getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> reported = exchange(
                "/api/comments/" + commentId + "/reports",
                HttpMethod.POST,
                userToken,
                Map.of("reason", "SPAM", "detail", "重复内容")
        );
        assertThat(reported.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> duplicate = exchange(
                "/api/comments/" + commentId + "/reports",
                HttpMethod.POST,
                userToken,
                Map.of("reason", "SPAM")
        );
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String adminToken = login("admin", "Admin@123456");
        ResponseEntity<JsonNode> reports = exchange(
                "/api/admin/comment-reports?status=OPEN",
                HttpMethod.GET,
                adminToken,
                null
        );
        assertThat(reports.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reports.getBody().get("data").get("total").asLong()).isEqualTo(1);

        ResponseEntity<JsonNode> pinned = exchange(
                "/api/admin/comments/" + commentId,
                HttpMethod.PATCH,
                adminToken,
                Map.of("action", "PIN")
        );
        assertThat(pinned.getBody().get("data").get("pinned").asBoolean()).isTrue();

        exchange("/api/admin/comments/" + commentId, HttpMethod.PATCH, adminToken, Map.of("action", "HIDE"));
        JsonNode hiddenList = restTemplate.getForEntity(
                url("/api/comments?targetType=SONG&targetId=1"),
                JsonNode.class
        ).getBody().get("data");
        assertThat(hiddenList.get("total").asLong()).isZero();

        exchange("/api/admin/comments/" + commentId, HttpMethod.PATCH, adminToken, Map.of("action", "RESTORE"));
        JsonNode restoredList = restTemplate.getForEntity(
                url("/api/comments?targetType=SONG&targetId=1"),
                JsonNode.class
        ).getBody().get("data");
        assertThat(restoredList.get("total").asLong()).isEqualTo(1);
    }

    @Test
    void contentAndPerMinuteRateLimitsAreEnforced() {
        String token = login("demo", "User@123456");
        ResponseEntity<JsonNode> tooLong = exchange(
                "/api/comments",
                HttpMethod.POST,
                token,
                Map.of("targetType", "SONG", "targetId", 1, "content", "好".repeat(501))
        );
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        for (int index = 0; index < 5; index++) {
            ResponseEntity<JsonNode> created = exchange(
                    "/api/comments",
                    HttpMethod.POST,
                    token,
                    Map.of("targetType", "SONG", "targetId", 1, "content", "限流评论 " + index)
            );
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<JsonNode> limited = exchange(
                "/api/comments",
                HttpMethod.POST,
                token,
                Map.of("targetType", "SONG", "targetId", 1, "content", "第六条")
        );
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private String login(String username, String password) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url("/api/auth/login"),
                Map.of("username", username, "password", password),
                JsonNode.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
