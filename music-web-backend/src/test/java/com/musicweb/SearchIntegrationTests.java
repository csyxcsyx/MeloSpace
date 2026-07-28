package com.musicweb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.exception.BusinessException;
import com.musicweb.service.SearchService;
import com.musicweb.vo.AlbumResponse;
import com.musicweb.vo.ArtistResponse;
import com.musicweb.vo.PlaylistResponse;
import com.musicweb.vo.PublicUserResponse;
import com.musicweb.vo.SearchResponse;
import com.musicweb.vo.SearchSuggestionResponse;
import com.musicweb.vo.SongResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:music_web_search;"
                + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;"
                + "DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@Transactional
class SearchIntegrationTests {

    @Autowired
    private SearchService searchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ranksSongMatchesAndPaginatesAcrossTitleArtistAndAlbum() {
        long artistId = insertArtist("Ranking Artist");
        long albumId = insertAlbum("Ranking Album", artistId);
        insertSong("Pulse", artistId, albumId, 1, 1);
        insertSong("Pulsewave", artistId, albumId, 10_000, 1);
        insertSong("Night Pulse", artistId, albumId, 100_000, 1);
        insertSong("Pulse Hidden", artistId, albumId, 1_000_000, 0);

        PageResult<SongResponse> firstPage = searchService.searchSongs("  pulse  ", 1, 2);
        PageResult<SongResponse> secondPage = searchService.searchSongs("pulse", 2, 2);

        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.items()).extracting(SongResponse::title)
                .containsExactly("Pulse", "Pulsewave");
        assertThat(secondPage.items()).extracting(SongResponse::title)
                .containsExactly("Night Pulse");

        long matchingArtistId = insertArtist("Needle Artist");
        long otherAlbumId = insertAlbum("Ordinary Album", matchingArtistId);
        insertSong("Unrelated Song", matchingArtistId, otherAlbumId, 0, 1);
        long ordinaryArtistId = insertArtist("Ordinary Artist");
        long matchingAlbumId = insertAlbum("Needle Collection", ordinaryArtistId);
        insertSong("Another Song", ordinaryArtistId, matchingAlbumId, 0, 1);
        assertThat(searchService.searchSongs("needle", 1, 20).items())
                .extracting(SongResponse::title)
                .contains("Unrelated Song", "Another Song");
    }

    @Test
    void treatsPercentUnderscoreAndEscapeMarkerAsLiteralText() {
        long artistId = insertArtist("Wildcard Artist");
        long albumId = insertAlbum("Wildcard Album", artistId);
        insertSong("100% Real", artistId, albumId, 0, 1);
        insertSong("100 Percent Real", artistId, albumId, 0, 1);
        insertSong("under_score", artistId, albumId, 0, 1);
        insertSong("underXscore", artistId, albumId, 0, 1);
        insertSong("Bang! Song", artistId, albumId, 0, 1);

        assertThat(searchService.searchSongs("100%", 1, 20).items())
                .extracting(SongResponse::title)
                .containsExactly("100% Real");
        assertThat(searchService.searchSongs("_", 1, 20).items())
                .extracting(SongResponse::title)
                .containsExactly("under_score");
        assertThat(searchService.searchSongs("!", 1, 20).items())
                .extracting(SongResponse::title)
                .containsExactly("Bang! Song");
    }

    @Test
    void ranksEverySearchTypeByExactPrefixThenContains() {
        long artistId = insertArtist("Orbit");
        insertArtist("Orbitals");
        insertArtist("The Orbit");
        assertThat(searchService.searchArtists("orbit", 1, 20).items())
                .extracting(ArtistResponse::name)
                .containsSubsequence("Orbit", "Orbitals", "The Orbit");

        insertAlbum("Orbit", artistId);
        insertAlbum("Orbit Sessions", artistId);
        insertAlbum("Beyond Orbit", artistId);
        assertThat(searchService.searchAlbums("orbit", 1, 20).items())
                .extracting(AlbumResponse::title)
                .containsSubsequence("Orbit", "Orbit Sessions", "Beyond Orbit");

        long ownerId = insertUser("orbit-owner", "Orbit", 1);
        insertUser("orbit-prefix", "Orbit Listener", 1);
        insertUser("orbit-contains", "The Orbit Listener", 1);
        long exactPlaylistId = insertPlaylist(ownerId, "Orbit", "PUBLIC");
        long prefixPlaylistId = insertPlaylist(ownerId, "Orbit Mix", "PUBLIC");
        long containsPlaylistId = insertPlaylist(ownerId, "The Orbit Mix", "PUBLIC");
        jdbcTemplate.update(
                "UPDATE playlist SET favorite_count = 0 WHERE id = ?",
                exactPlaylistId
        );
        jdbcTemplate.update(
                "UPDATE playlist SET favorite_count = 100 WHERE id IN (?, ?)",
                prefixPlaylistId,
                containsPlaylistId
        );

        assertThat(searchService.searchPlaylists("orbit", 1, 20).items())
                .extracting(PlaylistResponse::title)
                .containsSubsequence("Orbit", "Orbit Mix", "The Orbit Mix");
        assertThat(searchService.searchUsers("orbit", 1, 20).items())
                .extracting(PublicUserResponse::nickname)
                .containsSubsequence("Orbit", "Orbit Listener", "The Orbit Listener");
    }

    @Test
    void searchesOnlyPublicContentAndReturnsPrivacySafeUserStatistics() {
        long ownerId = insertUser("search-private-login", "Echo Creator", 1);
        long disabledUserId = insertUser("disabled-private-login", "Echo Disabled", 0);
        long artistId = insertArtist("Echo Artist");
        long albumId = insertAlbum("Echo Album", artistId);
        long publishedSongId = insertSong("Echo Song", artistId, albumId, 0, 1);
        long hiddenSongId = insertSong("Echo Hidden Song", artistId, albumId, 0, 0);
        long publicPlaylistId = insertPlaylist(ownerId, "Echo Public Mix", "PUBLIC");
        long privatePlaylistId = insertPlaylist(ownerId, "Echo Private Mix", "PRIVATE");

        jdbcTemplate.update(
                "INSERT INTO favorite (user_id, target_type, target_id) VALUES (?, 'PLAYLIST', ?)",
                disabledUserId,
                publicPlaylistId
        );
        insertComment(ownerId, "SONG", publishedSongId, 1);
        insertComment(ownerId, "SONG", hiddenSongId, 1);
        insertComment(ownerId, "PLAYLIST", publicPlaylistId, 1);
        insertComment(ownerId, "PLAYLIST", privatePlaylistId, 1);
        insertComment(ownerId, "SONG", publishedSongId, 0);

        SearchResponse response = searchService.search("echo");
        assertThat(response.songs()).extracting(SongResponse::title)
                .contains("Echo Song")
                .doesNotContain("Echo Hidden Song");
        assertThat(response.playlists()).extracting(item -> item.title())
                .contains("Echo Public Mix")
                .doesNotContain("Echo Private Mix");
        assertThat(response.users()).extracting(PublicUserResponse::nickname)
                .containsExactly("Echo Creator");
        assertThat(response.totals().users()).isEqualTo(1);

        PublicUserResponse user = response.users().get(0);
        assertThat(user.publicPlaylistCount()).isEqualTo(1);
        assertThat(user.receivedFavoriteCount()).isEqualTo(1);
        assertThat(user.commentCount()).isEqualTo(2);

        PageResult<PublicUserResponse> usersPage = searchService.searchUsers("Echo", 1, 20);
        assertThat(usersPage.items()).containsExactly(user);
    }

    @Test
    void validatesKeywordsPagesAndSuggestionLimits() {
        SearchResponse empty = searchService.search("   ");
        assertThat(empty.songs()).isEmpty();
        assertThat(empty.users()).isEmpty();
        assertThat(empty.totals().songs()).isZero();
        assertThat(empty.totals().artists()).isZero();
        assertThat(empty.totals().albums()).isZero();
        assertThat(empty.totals().playlists()).isZero();
        assertThat(empty.totals().users()).isZero();

        assertParameterError(() -> searchService.searchSongs("", 1, 20));
        assertParameterError(() -> searchService.searchSongs("x".repeat(51), 1, 20));
        assertParameterError(() -> searchService.searchSongs("valid", 0, 20));
        assertParameterError(() -> searchService.searchSongs("valid", 1, 101));
        assertParameterError(() -> searchService.suggestions("valid", 21));

        assertThat(searchService.searchSongs("x".repeat(50), 1, 100).items()).isEmpty();
    }

    @Test
    void suggestionsExposeACompactStableContract() {
        long artistId = insertArtist("Suggest Artist");
        long albumId = insertAlbum("Suggest Album", artistId);
        insertSong("Suggest Song", artistId, albumId, 0, 1);

        List<SearchSuggestionResponse> suggestions = searchService.suggestions("suggest", 3);

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions).extracting(SearchSuggestionResponse::type)
                .containsExactly("SONG", "ARTIST", "ALBUM");
        assertThat(suggestions.get(0).title()).isEqualTo("Suggest Song");
        assertThat(suggestions.get(0).subtitle()).isEqualTo("Suggest Artist · Suggest Album");
    }

    private void assertParameterError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.PARAM_ERROR);
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                });
    }

    private long insertArtist(String name) {
        jdbcTemplate.update("INSERT INTO artist (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM artist WHERE name = ?", Long.class, name);
    }

    private long insertAlbum(String title, long artistId) {
        jdbcTemplate.update("INSERT INTO album (title, artist_id) VALUES (?, ?)", title, artistId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM album WHERE title = ? AND artist_id = ?",
                Long.class,
                title,
                artistId
        );
    }

    private long insertSong(
            String title,
            long artistId,
            long albumId,
            long playCount,
            int status
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO song (
                          title, artist_id, album_id, audio_url, duration_seconds, play_count, status
                        ) VALUES (?, ?, ?, ?, 0, ?, ?)
                        """,
                title,
                artistId,
                albumId,
                "/media/test/" + title,
                playCount,
                status
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM song WHERE title = ? AND artist_id = ?",
                Long.class,
                title,
                artistId
        );
    }

    private long insertUser(String username, String nickname, int status) {
        jdbcTemplate.update(
                """
                        INSERT INTO `user` (
                          username, password_hash, nickname, bio, role, status
                        ) VALUES (?, 'not-used-by-search', ?, 'Public biography', 'USER', ?)
                        """,
                username,
                nickname,
                status
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE username = ?",
                Long.class,
                username
        );
    }

    private long insertPlaylist(long userId, String title, String visibility) {
        jdbcTemplate.update(
                "INSERT INTO playlist (user_id, title, visibility) VALUES (?, ?, ?)",
                userId,
                title,
                visibility
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM playlist WHERE user_id = ? AND title = ?",
                Long.class,
                userId,
                title
        );
    }

    private void insertComment(long userId, String targetType, long targetId, int status) {
        jdbcTemplate.update(
                """
                        INSERT INTO comment (
                          user_id, target_type, target_id, content, status
                        ) VALUES (?, ?, ?, 'search test comment', ?)
                        """,
                userId,
                targetType,
                targetId,
                status
        );
    }
}
