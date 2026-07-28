package com.musicweb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:music_web_public_profiles;"
                        + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "music-web.media.storage-root=target/test-public-profile-media"
        }
)
class UserPublicProfileIntegrationTests {

    private static final byte[] VALID_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetProfileFixtures() {
        jdbcTemplate.update("DELETE FROM upload_file");
        jdbcTemplate.update("DELETE FROM comment");
        jdbcTemplate.update("DELETE FROM favorite");
        jdbcTemplate.update("DELETE FROM playlist_song");
        jdbcTemplate.update("DELETE FROM playlist");
        jdbcTemplate.update("DELETE FROM `user` WHERE id = 4");
        jdbcTemplate.update(
                "UPDATE `user` SET nickname = ?, avatar_url = NULL, bio = NULL, status = 1 WHERE id = 2",
                "演示用户"
        );
        jdbcTemplate.update("""
                INSERT INTO `user` (id, username, password_hash, nickname, bio, role, status)
                SELECT 4, 'disabled-profile', password_hash, '已禁用用户', '不可公开', 'USER', 0
                FROM `user` WHERE id = 2
                """);
        jdbcTemplate.update("""
                INSERT INTO playlist
                    (id, user_id, title, visibility, play_count, favorite_count, created_at, updated_at)
                VALUES
                    (101, 2, '公开歌单', 'PUBLIC', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (102, 2, '私有歌单', 'PRIVATE', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (103, 4, '禁用用户歌单', 'PUBLIC', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO favorite (id, user_id, target_type, target_id)
                VALUES
                    (201, 1, 'PLAYLIST', 101),
                    (202, 1, 'PLAYLIST', 102)
                """);
        jdbcTemplate.update("""
                INSERT INTO comment (id, user_id, target_type, target_id, content, status)
                VALUES
                    (301, 2, 'SONG', 1, '上架歌曲评论', 1),
                    (302, 2, 'SONG', 2, '下架歌曲评论', 1),
                    (303, 2, 'PLAYLIST', 101, '公开歌单评论', 1),
                    (304, 2, 'PLAYLIST', 102, '私有歌单评论', 1),
                    (305, 2, 'SONG', 1, '隐藏评论', 0)
                """);
    }

    @Test
    void publicProfileExposesOnlyPublicFieldsAndVisibleStatistics() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url("/api/users/2"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode user = response.getBody().get("data");
        assertThat(user.get("id").asLong()).isEqualTo(2);
        assertThat(user.get("nickname").asText()).isEqualTo("演示用户");
        assertThat(user.get("publicPlaylistCount").asLong()).isEqualTo(1);
        assertThat(user.get("receivedFavoriteCount").asLong()).isEqualTo(1);
        assertThat(user.get("commentCount").asLong()).isEqualTo(2);
        assertThat(user.has("username")).isFalse();
        assertThat(user.has("role")).isFalse();
        assertThat(user.has("status")).isFalse();
        assertThat(user.has("passwordHash")).isFalse();
    }

    @Test
    void publicProfileRejectsDisabledMissingAndNonNumericUsersWithoutOpeningMe() {
        assertThat(restTemplate.getForEntity(url("/api/users/4"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity(url("/api/users/9999"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity(url("/api/users/4/playlists"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity(url("/api/users/me"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity(url("/api/users/not-a-number"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicUserPlaylistsArePagedAndNeverIncludePrivatePlaylists() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                url("/api/users/2/playlists?page=1&size=1"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = response.getBody().get("data");
        assertThat(page.get("page").asLong()).isEqualTo(1);
        assertThat(page.get("size").asLong()).isEqualTo(1);
        assertThat(page.get("total").asLong()).isEqualTo(1);
        assertThat(page.get("items")).hasSize(1);
        assertThat(page.get("items").get(0).get("id").asLong()).isEqualTo(101);
        assertThat(response.getBody().toString()).doesNotContain("私有歌单");
    }

    @Test
    void authenticatedUserCanUpdateProfileAndValidationPreservesExistingData() {
        String token = login("demo", "User@123456");
        ResponseEntity<JsonNode> updated = exchangeWithToken(
                "/api/users/me",
                HttpMethod.PUT,
                token,
                Map.of(
                        "nickname", "新的昵称",
                        "avatarUrl", "/media/avatar/profile.png",
                        "bio", "这是一段公开简介"
                )
        );

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode summary = updated.getBody().get("data");
        assertThat(summary.get("username").asText()).isEqualTo("demo");
        assertThat(summary.get("nickname").asText()).isEqualTo("新的昵称");
        assertThat(summary.get("avatarUrl").asText()).isEqualTo("/media/avatar/profile.png");
        assertThat(summary.get("bio").asText()).isEqualTo("这是一段公开简介");

        ResponseEntity<JsonNode> publicProfile = restTemplate.getForEntity(url("/api/users/2"), JsonNode.class);
        assertThat(publicProfile.getBody().get("data").get("bio").asText()).isEqualTo("这是一段公开简介");

        ResponseEntity<JsonNode> blankNickname = exchangeWithToken(
                "/api/users/me",
                HttpMethod.PUT,
                token,
                Map.of("nickname", "   ", "bio", "不应写入")
        );
        assertThat(blankNickname.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> oversizedBio = exchangeWithToken(
                "/api/users/me",
                HttpMethod.PUT,
                token,
                Map.of("nickname", "合法昵称", "bio", "字".repeat(501))
        );
        assertThat(oversizedBio.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> afterFailures = exchangeWithToken(
                "/api/users/me",
                HttpMethod.GET,
                token,
                null
        );
        assertThat(afterFailures.getBody().get("data").get("nickname").asText()).isEqualTo("新的昵称");
        assertThat(afterFailures.getBody().get("data").get("bio").asText()).isEqualTo("这是一段公开简介");
    }

    @Test
    void imageUploadRequiresAuthenticationAndStrictPurposeAndImageContent() {
        ResponseEntity<JsonNode> noToken = uploadImage(null, "AVATAR", "avatar.png", "image/png", VALID_PNG);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = login("demo", "User@123456");
        ResponseEntity<JsonNode> invalidPurpose = uploadImage(
                token,
                "ALBUM",
                "avatar.png",
                "image/png",
                VALID_PNG
        );
        assertThat(invalidPurpose.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> fakeImage = uploadImage(
                token,
                "AVATAR",
                "avatar.png",
                "image/png",
                "not-an-image".getBytes(StandardCharsets.UTF_8)
        );
        assertThat(fakeImage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> mismatchedExtension = uploadImage(
                token,
                "AVATAR",
                "avatar.jpg",
                "image/png",
                VALID_PNG
        );
        assertThat(mismatchedExtension.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> unsupportedExtension = uploadImage(
                token,
                "AVATAR",
                "avatar.gif",
                "image/gif",
                VALID_PNG
        );
        assertThat(unsupportedExtension.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> oversized = uploadImage(
                token,
                "PLAYLIST_COVER",
                "cover.png",
                "image/png",
                new byte[5 * 1024 * 1024 + 1]
        );
        assertThat(oversized.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> uploaded = uploadImage(
                token,
                "PLAYLIST_COVER",
                "cover.png",
                "image/png",
                VALID_PNG
        );
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = uploaded.getBody().get("data");
        assertThat(data.get("fileType").asText()).isEqualTo("PLAYLIST_COVER");
        assertThat(data.get("url").asText()).startsWith("/media/playlist-cover/");
        assertThat(data.get("mimeType").asText()).isEqualTo("image/png");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM upload_file WHERE owner_id = 2 AND file_type = 'PLAYLIST_COVER'",
                Long.class
        )).isEqualTo(1L);
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

    private ResponseEntity<JsonNode> exchangeWithToken(
            String path,
            HttpMethod method,
            String token,
            Object body
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> uploadImage(
            String token,
            String purpose,
            String filename,
            String contentType,
            byte[] bytes
    ) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("purpose", purpose);
        body.add("file", new HttpEntity<>(new NamedByteArrayResource(bytes, filename), fileHeaders));

        return restTemplate.exchange(
                url("/api/uploads/images"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
