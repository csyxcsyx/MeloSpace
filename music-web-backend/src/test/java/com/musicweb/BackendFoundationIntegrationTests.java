package com.musicweb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
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
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendFoundationIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointIsPublic() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url("/actuator/health"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status").asText()).isEqualTo("UP");
    }

    @Test
    void authFlowUsesUnifiedResponsesAndRoleChecks() {
        ResponseEntity<JsonNode> noToken = restTemplate.getForEntity(url("/api/users/me"), JsonNode.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(noToken.getBody().get("code").asInt()).isEqualTo(401);

        JsonNode adminLogin = login("admin", "Admin@123456");
        String adminToken = adminLogin.get("data").get("token").asText();

        ResponseEntity<JsonNode> me = exchangeWithToken("/api/users/me", HttpMethod.GET, adminToken, null);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("data").get("username").asText()).isEqualTo("admin");

        ResponseEntity<JsonNode> dashboard = exchangeWithToken("/api/admin/dashboard", HttpMethod.GET, adminToken, null);
        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashboard.getBody().get("data").get("users").asLong()).isGreaterThanOrEqualTo(2);

        JsonNode userLogin = login("demo", "User@123456");
        String userToken = userLogin.get("data").get("token").asText();
        ResponseEntity<JsonNode> forbidden = exchangeWithToken("/api/admin/dashboard", HttpMethod.GET, userToken, null);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().get("code").asInt()).isEqualTo(403);
    }

    @Test
    void registrationRejectsDuplicateUsername() {
        Map<String, String> payload = Map.of(
                "username", "new_user",
                "password", "NewUser@123456",
                "nickname", "New User"
        );
        ResponseEntity<JsonNode> created = restTemplate.postForEntity(url("/api/auth/register"), payload, JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody().get("code").asInt()).isZero();
        assertThat(created.getBody().get("data").get("token").asText()).isNotBlank();

        ResponseEntity<JsonNode> duplicate = restTemplate.postForEntity(url("/api/auth/register"), payload, JsonNode.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code").asInt()).isEqualTo(409);
    }

    @Test
    void publicMusicQueriesOnlyExposePublishedContent() {
        ResponseEntity<JsonNode> songs = restTemplate.getForEntity(
                url("/api/songs?page=1&size=10"),
                JsonNode.class
        );
        assertThat(songs.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode songItems = songs.getBody().get("data").get("items");
        assertThat(songItems).hasSize(1);
        assertThat(songItems.get(0).get("title").asText()).isEqualTo("I Do");
        assertThat(songItems.get(0).get("artistName").asText()).isEqualTo("周杰伦");
        assertThat(songItems.get(0).get("albumTitle").asText()).isEqualTo("太阳之子");

        ResponseEntity<JsonNode> keyword = restTemplate.getForEntity(
                url("/api/songs?keyword=I&page=1&size=10"),
                JsonNode.class
        );
        assertThat(keyword.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(keyword.getBody().get("data").get("total").asLong()).isEqualTo(1);

        ResponseEntity<JsonNode> publishedDetail = restTemplate.getForEntity(url("/api/songs/1"), JsonNode.class);
        assertThat(publishedDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publishedDetail.getBody().get("data").get("audioUrl").asText()).contains("/media/audio/");

        ResponseEntity<JsonNode> unpublishedDetail = restTemplate.getForEntity(url("/api/songs/2"), JsonNode.class);
        assertThat(unpublishedDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unpublishedDetail.getBody().get("code").asInt()).isEqualTo(404);
    }

    @Test
    void searchArtistsAndAlbumsUseUnifiedResponses() {
        ResponseEntity<JsonNode> search = restTemplate.getForEntity(url("/api/search?keyword=周杰伦"), JsonNode.class);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(search.getBody().get("data").get("artists").get(0).get("name").asText()).isEqualTo("周杰伦");

        ResponseEntity<JsonNode> artists = restTemplate.getForEntity(url("/api/artists"), JsonNode.class);
        assertThat(artists.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(artists.getBody().get("data").get(0).get("name").asText()).isEqualTo("周杰伦");

        ResponseEntity<JsonNode> albums = restTemplate.getForEntity(url("/api/albums?artistId=1"), JsonNode.class);
        assertThat(albums.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(albums.getBody().get("data").get(0).get("title").asText()).isEqualTo("太阳之子");
        assertThat(albums.getBody().get("data").get(0).get("artistName").asText()).isEqualTo("周杰伦");
    }

    private JsonNode login(String username, String password) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url("/api/auth/login"),
                Map.of("username", username, "password", password),
                JsonNode.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code").asInt()).isZero();
        return response.getBody();
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
