package com.musicweb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
                url("/api/songs?keyword=I&page=1&size=10"),
                JsonNode.class
        );
        assertThat(songs.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode songItems = songs.getBody().get("data").get("items");
        assertThat(songItems).hasSize(1);
        assertThat(songItems.get(0).get("title").asText()).isEqualTo("I Do");
        assertThat(songItems.get(0).get("artistName").asText()).isEqualTo("周杰伦");
        assertThat(songItems.get(0).get("albumTitle").asText()).isEqualTo("太阳之子");

        ResponseEntity<JsonNode> unpublishedKeyword = restTemplate.getForEntity(
                url("/api/songs?keyword=下架测试歌&page=1&size=10"),
                JsonNode.class
        );
        assertThat(unpublishedKeyword.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unpublishedKeyword.getBody().get("data").get("total").asLong()).isZero();

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

    @Test
    void adminCanManageMusicContentAndPublicationStatus() {
        ResponseEntity<JsonNode> noToken = restTemplate.getForEntity(url("/api/admin/songs"), JsonNode.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String userToken = login("demo", "User@123456").get("data").get("token").asText();
        ResponseEntity<JsonNode> forbidden = exchangeWithToken("/api/admin/songs", HttpMethod.GET, userToken, null);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String adminToken = login("admin", "Admin@123456").get("data").get("token").asText();
        ResponseEntity<JsonNode> artist = exchangeWithToken(
                "/api/admin/artists",
                HttpMethod.POST,
                adminToken,
                Map.of("name", "阶段三歌手", "bio", "管理员接口测试", "avatarUrl", "/media/cover/artist.jpg")
        );
        assertThat(artist.getStatusCode()).isEqualTo(HttpStatus.OK);
        long artistId = artist.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> updatedArtist = exchangeWithToken(
                "/api/admin/artists/" + artistId,
                HttpMethod.PUT,
                adminToken,
                Map.of("name", "阶段三歌手改", "bio", "已编辑", "avatarUrl", "/media/cover/artist2.jpg")
        );
        assertThat(updatedArtist.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updatedArtist.getBody().get("data").get("name").asText()).isEqualTo("阶段三歌手改");

        ResponseEntity<JsonNode> album = exchangeWithToken(
                "/api/admin/albums",
                HttpMethod.POST,
                adminToken,
                Map.of("title", "阶段三专辑", "artistId", artistId, "coverUrl", "/media/cover/album.jpg")
        );
        assertThat(album.getStatusCode()).isEqualTo(HttpStatus.OK);
        long albumId = album.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> updatedAlbum = exchangeWithToken(
                "/api/admin/albums/" + albumId,
                HttpMethod.PUT,
                adminToken,
                Map.of("title", "阶段三专辑改", "artistId", artistId, "coverUrl", "/media/cover/album2.jpg")
        );
        assertThat(updatedAlbum.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updatedAlbum.getBody().get("data").get("title").asText()).isEqualTo("阶段三专辑改");

        ResponseEntity<JsonNode> mismatch = exchangeWithToken(
                "/api/admin/songs",
                HttpMethod.POST,
                adminToken,
                Map.of("title", "关系错误歌曲", "artistId", artistId, "albumId", 1, "audioUrl", "/media/audio/bad.flac")
        );
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mismatch.getBody().get("code").asInt()).isEqualTo(400);

        ResponseEntity<JsonNode> song = exchangeWithToken(
                "/api/admin/songs",
                HttpMethod.POST,
                adminToken,
                Map.of(
                        "title", "阶段三歌曲",
                        "artistId", artistId,
                        "albumId", albumId,
                        "audioUrl", "/media/audio/stage3.flac",
                        "durationSeconds", 180,
                        "status", 1
                )
        );
        assertThat(song.getStatusCode()).isEqualTo(HttpStatus.OK);
        long songId = song.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> adminList = exchangeWithToken(
                "/api/admin/songs?keyword=阶段三歌曲&page=1&size=10",
                HttpMethod.GET,
                adminToken,
                null
        );
        assertThat(adminList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminList.getBody().get("data").get("total").asLong()).isEqualTo(1);

        ResponseEntity<JsonNode> offline = exchangeWithToken(
                "/api/admin/songs/" + songId + "/status",
                HttpMethod.PATCH,
                adminToken,
                Map.of("status", 0)
        );
        assertThat(offline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(offline.getBody().get("data").get("status").asInt()).isZero();

        ResponseEntity<JsonNode> publicDetail = restTemplate.getForEntity(url("/api/songs/" + songId), JsonNode.class);
        assertThat(publicDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> adminOfflineList = exchangeWithToken(
                "/api/admin/songs?keyword=阶段三歌曲&status=0&page=1&size=10",
                HttpMethod.GET,
                adminToken,
                null
        );
        assertThat(adminOfflineList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminOfflineList.getBody().get("data").get("items").get(0).get("id").asLong()).isEqualTo(songId);

        ResponseEntity<JsonNode> online = exchangeWithToken(
                "/api/admin/songs/" + songId + "/status",
                HttpMethod.PATCH,
                adminToken,
                Map.of("status", 1)
        );
        assertThat(online.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity(url("/api/songs/" + songId), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminCanUploadLocalMediaAndUseReturnedUrlForSongs() {
        String adminToken = login("admin", "Admin@123456").get("data").get("token").asText();
        ResponseEntity<JsonNode> upload = uploadWithToken(
                adminToken,
                "AUDIO",
                "stage3.mp3",
                "audio/mpeg",
                "fake audio".getBytes(StandardCharsets.UTF_8)
        );
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode uploadData = upload.getBody().get("data");
        assertThat(uploadData.get("fileType").asText()).isEqualTo("AUDIO");
        assertThat(uploadData.get("url").asText()).startsWith("/media/audio/");
        String audioUrl = uploadData.get("url").asText();

        ResponseEntity<String> staticMedia = restTemplate.getForEntity(url(audioUrl), String.class);
        assertThat(staticMedia.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staticMedia.getBody()).isEqualTo("fake audio");

        ResponseEntity<JsonNode> createdSong = exchangeWithToken(
                "/api/admin/songs",
                HttpMethod.POST,
                adminToken,
                Map.of(
                        "title", "上传闭环歌曲",
                        "artistId", 1,
                        "albumId", 1,
                        "audioUrl", audioUrl,
                        "durationSeconds", 9,
                        "status", 1
                )
        );
        assertThat(createdSong.getStatusCode()).isEqualTo(HttpStatus.OK);
        long songId = createdSong.getBody().get("data").get("id").asLong();
        ResponseEntity<JsonNode> publicSong = restTemplate.getForEntity(url("/api/songs/" + songId), JsonNode.class);
        assertThat(publicSong.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicSong.getBody().get("data").get("audioUrl").asText()).isEqualTo(audioUrl);

        ResponseEntity<JsonNode> badExtension = uploadWithToken(
                adminToken,
                "AUDIO",
                "stage3.exe",
                "application/octet-stream",
                "bad".getBytes(StandardCharsets.UTF_8)
        );
        assertThat(badExtension.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badExtension.getBody().get("code").asInt()).isEqualTo(400);

        ResponseEntity<JsonNode> badType = uploadWithToken(
                adminToken,
                "VIDEO",
                "stage3.mp3",
                "audio/mpeg",
                "bad".getBytes(StandardCharsets.UTF_8)
        );
        assertThat(badType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badType.getBody().get("code").asInt()).isEqualTo(400);

        ResponseEntity<JsonNode> missingFile = uploadMissingFile(adminToken);
        assertThat(missingFile.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingFile.getBody().get("code").asInt()).isEqualTo(400);
    }

    @Test
    void playlistApisAllowUserBusinessFlowWithOwnershipRules() {
        ResponseEntity<JsonNode> unauthenticatedCreate = restTemplate.postForEntity(
                url("/api/playlists"),
                Map.of("title", "未登录歌单"),
                JsonNode.class
        );
        assertThat(unauthenticatedCreate.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String demoToken = login("demo", "User@123456").get("data").get("token").asText();
        String adminToken = login("admin", "Admin@123456").get("data").get("token").asText();

        ResponseEntity<JsonNode> created = exchangeWithToken(
                "/api/playlists",
                HttpMethod.POST,
                demoToken,
                Map.of(
                        "title", "阶段四公开歌单",
                        "description", "用户闭环测试",
                        "coverUrl", "/media/cover/stage4.jpg",
                        "visibility", "PUBLIC"
                )
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long playlistId = created.getBody().get("data").get("id").asLong();
        assertThat(created.getBody().get("data").get("visibility").asText()).isEqualTo("PUBLIC");

        ResponseEntity<JsonNode> myPlaylists = exchangeWithToken(
                "/api/users/me/playlists?page=1&size=10",
                HttpMethod.GET,
                demoToken,
                null
        );
        assertThat(myPlaylists.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(myPlaylists.getBody().get("data").get("total").asLong()).isGreaterThanOrEqualTo(1);

        ResponseEntity<JsonNode> publicList = restTemplate.getForEntity(
                url("/api/playlists?keyword=阶段四公开歌单&page=1&size=10"),
                JsonNode.class
        );
        assertThat(publicList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicList.getBody().get("data").get("total").asLong()).isEqualTo(1);

        ResponseEntity<JsonNode> addFirstSong = exchangeWithToken(
                "/api/playlists/" + playlistId + "/songs",
                HttpMethod.POST,
                demoToken,
                Map.of("songId", 1)
        );
        assertThat(addFirstSong.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(addFirstSong.getBody().get("data").get("songs")).hasSize(1);

        ResponseEntity<JsonNode> duplicateAdd = exchangeWithToken(
                "/api/playlists/" + playlistId + "/songs",
                HttpMethod.POST,
                demoToken,
                Map.of("songId", 1)
        );
        assertThat(duplicateAdd.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicateAdd.getBody().get("data").get("songs")).hasSize(1);

        ResponseEntity<JsonNode> addOfflineSong = exchangeWithToken(
                "/api/playlists/" + playlistId + "/songs",
                HttpMethod.POST,
                demoToken,
                Map.of("songId", 2)
        );
        assertThat(addOfflineSong.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> secondSong = exchangeWithToken(
                "/api/admin/songs",
                HttpMethod.POST,
                adminToken,
                Map.of(
                        "title", "阶段四排序歌曲",
                        "artistId", 1,
                        "albumId", 1,
                        "audioUrl", "/media/audio/stage4-order.flac",
                        "durationSeconds", 120,
                        "status", 1
                )
        );
        assertThat(secondSong.getStatusCode()).isEqualTo(HttpStatus.OK);
        long secondSongId = secondSong.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> addSecondSong = exchangeWithToken(
                "/api/playlists/" + playlistId + "/songs",
                HttpMethod.POST,
                demoToken,
                Map.of("songId", secondSongId)
        );
        assertThat(addSecondSong.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(addSecondSong.getBody().get("data").get("songs")).hasSize(2);

        ResponseEntity<JsonNode> reordered = exchangeWithToken(
                "/api/playlists/" + playlistId + "/songs/order",
                HttpMethod.PUT,
                demoToken,
                Map.of("songIds", new long[] {secondSongId, 1})
        );
        assertThat(reordered.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode reorderedSongs = reordered.getBody().get("data").get("songs");
        assertThat(reorderedSongs.get(0).get("songId").asLong()).isEqualTo(secondSongId);
        assertThat(reorderedSongs.get(1).get("songId").asLong()).isEqualTo(1);

        ResponseEntity<JsonNode> invalidOrder = exchangeWithToken(
                "/api/playlists/" + playlistId + "/songs/order",
                HttpMethod.PUT,
                demoToken,
                Map.of("songIds", new long[] {1})
        );
        assertThat(invalidOrder.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> forbiddenUpdate = exchangeWithToken(
                "/api/playlists/" + playlistId,
                HttpMethod.PUT,
                adminToken,
                Map.of("title", "越权编辑", "visibility", "PUBLIC")
        );
        assertThat(forbiddenUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> privatePlaylist = exchangeWithToken(
                "/api/playlists",
                HttpMethod.POST,
                demoToken,
                Map.of("title", "阶段四私有歌单", "visibility", "PRIVATE")
        );
        assertThat(privatePlaylist.getStatusCode()).isEqualTo(HttpStatus.OK);
        long privatePlaylistId = privatePlaylist.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> publicPrivateDetail = restTemplate.getForEntity(
                url("/api/playlists/" + privatePlaylistId),
                JsonNode.class
        );
        assertThat(publicPrivateDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> ownerPrivateDetail = exchangeWithToken(
                "/api/playlists/" + privatePlaylistId,
                HttpMethod.GET,
                demoToken,
                null
        );
        assertThat(ownerPrivateDetail.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> removedSong = exchangeWithToken(
                "/api/playlists/" + playlistId + "/songs/" + secondSongId,
                HttpMethod.DELETE,
                demoToken,
                null
        );
        assertThat(removedSong.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(removedSong.getBody().get("data").get("songs")).hasSize(1);

        ResponseEntity<JsonNode> deleted = exchangeWithToken(
                "/api/playlists/" + playlistId,
                HttpMethod.DELETE,
                demoToken,
                null
        );
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> deletedDetail = restTemplate.getForEntity(
                url("/api/playlists/" + playlistId),
                JsonNode.class
        );
        assertThat(deletedDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void userInteractionApisSupportFavoritesCommentsRecentPlaysAndPlaylistSearch() {
        ResponseEntity<JsonNode> unauthenticatedFavorites = restTemplate.getForEntity(
                url("/api/users/me/favorites"),
                JsonNode.class
        );
        assertThat(unauthenticatedFavorites.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> unauthenticatedFavoriteWrite = restTemplate.postForEntity(
                url("/api/favorites"),
                Map.of("targetType", "SONG", "targetId", 1),
                JsonNode.class
        );
        assertThat(unauthenticatedFavoriteWrite.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> unauthenticatedCommentWrite = restTemplate.postForEntity(
                url("/api/comments"),
                Map.of("targetType", "SONG", "targetId", 1, "content", "未登录评论"),
                JsonNode.class
        );
        assertThat(unauthenticatedCommentWrite.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> unauthenticatedPlayRecord = restTemplate.postForEntity(
                url("/api/songs/1/play-record"),
                Map.of("progressSeconds", 30, "sourceType", "SEARCH"),
                JsonNode.class
        );
        assertThat(unauthenticatedPlayRecord.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String demoToken = login("demo", "User@123456").get("data").get("token").asText();
        String adminToken = login("admin", "Admin@123456").get("data").get("token").asText();

        ResponseEntity<JsonNode> publicPlaylist = exchangeWithToken(
                "/api/playlists",
                HttpMethod.POST,
                demoToken,
                Map.of("title", "阶段四搜索公开歌单", "visibility", "PUBLIC")
        );
        assertThat(publicPlaylist.getStatusCode()).isEqualTo(HttpStatus.OK);
        long publicPlaylistId = publicPlaylist.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> privatePlaylist = exchangeWithToken(
                "/api/playlists",
                HttpMethod.POST,
                demoToken,
                Map.of("title", "阶段四搜索私有歌单", "visibility", "PRIVATE")
        );
        assertThat(privatePlaylist.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> favoriteSong = exchangeWithToken(
                "/api/favorites",
                HttpMethod.POST,
                demoToken,
                Map.of("targetType", "SONG", "targetId", 1)
        );
        assertThat(favoriteSong.getStatusCode()).isEqualTo(HttpStatus.OK);
        long favoriteSongId = favoriteSong.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> duplicateFavoriteSong = exchangeWithToken(
                "/api/favorites",
                HttpMethod.POST,
                demoToken,
                Map.of("targetType", "SONG", "targetId", 1)
        );
        assertThat(duplicateFavoriteSong.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicateFavoriteSong.getBody().get("data").get("id").asLong()).isEqualTo(favoriteSongId);

        ResponseEntity<JsonNode> favoritePlaylist = exchangeWithToken(
                "/api/favorites",
                HttpMethod.POST,
                demoToken,
                Map.of("targetType", "PLAYLIST", "targetId", publicPlaylistId)
        );
        assertThat(favoritePlaylist.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> favorites = exchangeWithToken(
                "/api/users/me/favorites?page=1&size=10",
                HttpMethod.GET,
                demoToken,
                null
        );
        assertThat(favorites.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(favorites.getBody().get("data").get("total").asLong()).isEqualTo(2);

        ResponseEntity<JsonNode> unfavoriteMissing = exchangeWithToken(
                "/api/favorites?targetType=SONG&targetId=9999",
                HttpMethod.DELETE,
                demoToken,
                null
        );
        assertThat(unfavoriteMissing.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> invalidFavorite = exchangeWithToken(
                "/api/favorites",
                HttpMethod.POST,
                demoToken,
                Map.of("targetType", "ALBUM", "targetId", 1)
        );
        assertThat(invalidFavorite.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> createdComment = exchangeWithToken(
                "/api/comments",
                HttpMethod.POST,
                demoToken,
                Map.of("targetType", "SONG", "targetId", 1, "content", "阶段四评论")
        );
        assertThat(createdComment.getStatusCode()).isEqualTo(HttpStatus.OK);
        long commentId = createdComment.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> comments = restTemplate.getForEntity(
                url("/api/comments?targetType=SONG&targetId=1&page=1&size=10"),
                JsonNode.class
        );
        assertThat(comments.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(comments.getBody().get("data").get("total").asLong()).isEqualTo(1);
        assertThat(comments.getBody().get("data").get("items").get(0).get("content").asText())
                .isEqualTo("阶段四评论");

        ResponseEntity<JsonNode> forbiddenCommentDelete = exchangeWithToken(
                "/api/comments/" + commentId,
                HttpMethod.DELETE,
                adminToken,
                null
        );
        assertThat(forbiddenCommentDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> deletedComment = exchangeWithToken(
                "/api/comments/" + commentId,
                HttpMethod.DELETE,
                demoToken,
                null
        );
        assertThat(deletedComment.getStatusCode()).isEqualTo(HttpStatus.OK);

        long playCountBefore = restTemplate
                .getForEntity(url("/api/songs/1"), JsonNode.class)
                .getBody()
                .get("data")
                .get("playCount")
                .asLong();
        ResponseEntity<JsonNode> playRecord = exchangeWithToken(
                "/api/songs/1/play-record",
                HttpMethod.POST,
                demoToken,
                Map.of("progressSeconds", 35, "sourceType", "SEARCH")
        );
        assertThat(playRecord.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(playRecord.getBody().get("data").get("progressSeconds").asInt()).isEqualTo(35);

        ResponseEntity<JsonNode> recentPlays = exchangeWithToken(
                "/api/users/me/recent-plays?page=1&size=10",
                HttpMethod.GET,
                demoToken,
                null
        );
        assertThat(recentPlays.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recentPlays.getBody().get("data").get("total").asLong()).isEqualTo(1);
        JsonNode recentPlay = recentPlays.getBody().get("data").get("items").get(0);
        assertThat(recentPlay.get("songId").asLong()).isEqualTo(1);
        assertThat(recentPlay.get("song").get("title").asText()).isEqualTo("I Do");
        assertThat(recentPlay.get("song").get("artistName").asText()).isEqualTo("周杰伦");

        long playCountAfter = restTemplate
                .getForEntity(url("/api/songs/1"), JsonNode.class)
                .getBody()
                .get("data")
                .get("playCount")
                .asLong();
        assertThat(playCountAfter).isEqualTo(playCountBefore + 1);

        ResponseEntity<JsonNode> search = restTemplate.getForEntity(
                url("/api/search?keyword=阶段四搜索"),
                JsonNode.class
        );
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        String playlistsJson = search.getBody().get("data").get("playlists").toString();
        assertThat(playlistsJson).contains("阶段四搜索公开歌单");
        assertThat(playlistsJson).doesNotContain("阶段四搜索私有歌单");
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

    private ResponseEntity<JsonNode> uploadWithToken(
            String token,
            String fileType,
            String filename,
            String contentType,
            byte[] bytes
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("fileType", fileType);
        body.add("file", new HttpEntity<>(new NamedByteArrayResource(bytes, filename), fileHeaders));

        return restTemplate.exchange(
                url("/api/admin/upload"),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );
    }

    private ResponseEntity<JsonNode> uploadMissingFile(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("fileType", "AUDIO");

        return restTemplate.exchange(
                url("/api/admin/upload"),
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
