package com.musicweb.mapper;

import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.Song;
import com.musicweb.mapper.projection.SearchUserProjection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SearchMapper {

    @Select("""
            SELECT s.*
            FROM song s
            JOIN artist ar ON ar.id = s.artist_id
            LEFT JOIN album al ON al.id = s.album_id
            WHERE s.status = 1
              AND (
                LOWER(s.title) LIKE #{contains} ESCAPE '!'
                OR LOWER(ar.name) LIKE #{contains} ESCAPE '!'
                OR LOWER(COALESCE(al.title, '')) LIKE #{contains} ESCAPE '!'
              )
            ORDER BY
              CASE
                WHEN LOWER(s.title) = #{exact}
                  OR LOWER(ar.name) = #{exact}
                  OR LOWER(COALESCE(al.title, '')) = #{exact} THEN 0
                WHEN LOWER(s.title) LIKE #{prefix} ESCAPE '!'
                  OR LOWER(ar.name) LIKE #{prefix} ESCAPE '!'
                  OR LOWER(COALESCE(al.title, '')) LIKE #{prefix} ESCAPE '!' THEN 1
                ELSE 2
              END,
              s.play_count DESC,
              s.updated_at DESC,
              s.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Song> searchSongs(
            @Param("exact") String exact,
            @Param("prefix") String prefix,
            @Param("contains") String contains,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    @Select("""
            SELECT COUNT(*)
            FROM song s
            JOIN artist ar ON ar.id = s.artist_id
            LEFT JOIN album al ON al.id = s.album_id
            WHERE s.status = 1
              AND (
                LOWER(s.title) LIKE #{contains} ESCAPE '!'
                OR LOWER(ar.name) LIKE #{contains} ESCAPE '!'
                OR LOWER(COALESCE(al.title, '')) LIKE #{contains} ESCAPE '!'
              )
            """)
    long countSongs(@Param("contains") String contains);

    @Select("""
            SELECT ar.*
            FROM artist ar
            WHERE LOWER(ar.name) LIKE #{contains} ESCAPE '!'
            ORDER BY
              CASE
                WHEN LOWER(ar.name) = #{exact} THEN 0
                WHEN LOWER(ar.name) LIKE #{prefix} ESCAPE '!' THEN 1
                ELSE 2
              END,
              ar.updated_at DESC,
              ar.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Artist> searchArtists(
            @Param("exact") String exact,
            @Param("prefix") String prefix,
            @Param("contains") String contains,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    @Select("""
            SELECT COUNT(*)
            FROM artist ar
            WHERE LOWER(ar.name) LIKE #{contains} ESCAPE '!'
            """)
    long countArtists(@Param("contains") String contains);

    @Select("""
            SELECT al.*
            FROM album al
            WHERE LOWER(al.title) LIKE #{contains} ESCAPE '!'
            ORDER BY
              CASE
                WHEN LOWER(al.title) = #{exact} THEN 0
                WHEN LOWER(al.title) LIKE #{prefix} ESCAPE '!' THEN 1
                ELSE 2
              END,
              al.updated_at DESC,
              al.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Album> searchAlbums(
            @Param("exact") String exact,
            @Param("prefix") String prefix,
            @Param("contains") String contains,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    @Select("""
            SELECT COUNT(*)
            FROM album al
            WHERE LOWER(al.title) LIKE #{contains} ESCAPE '!'
            """)
    long countAlbums(@Param("contains") String contains);

    @Select("""
            SELECT p.*
            FROM playlist p
            WHERE p.visibility = 'PUBLIC'
              AND (
                LOWER(p.title) LIKE #{contains} ESCAPE '!'
                OR LOWER(COALESCE(p.description, '')) LIKE #{contains} ESCAPE '!'
              )
            ORDER BY
              CASE
                WHEN LOWER(p.title) = #{exact}
                  OR LOWER(COALESCE(p.description, '')) = #{exact} THEN 0
                WHEN LOWER(p.title) LIKE #{prefix} ESCAPE '!'
                  OR LOWER(COALESCE(p.description, '')) LIKE #{prefix} ESCAPE '!' THEN 1
                ELSE 2
              END,
              p.favorite_count DESC,
              p.play_count DESC,
              p.updated_at DESC,
              p.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Playlist> searchPlaylists(
            @Param("exact") String exact,
            @Param("prefix") String prefix,
            @Param("contains") String contains,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    @Select("""
            SELECT COUNT(*)
            FROM playlist p
            WHERE p.visibility = 'PUBLIC'
              AND (
                LOWER(p.title) LIKE #{contains} ESCAPE '!'
                OR LOWER(COALESCE(p.description, '')) LIKE #{contains} ESCAPE '!'
              )
            """)
    long countPlaylists(@Param("contains") String contains);

    @Select("""
            SELECT
              u.id,
              u.nickname,
              u.avatar_url,
              u.bio,
              (
                SELECT COUNT(*)
                FROM playlist owned_playlist
                WHERE owned_playlist.user_id = u.id
                  AND owned_playlist.visibility = 'PUBLIC'
              ) AS public_playlist_count,
              (
                SELECT COUNT(*)
                FROM favorite received_favorite
                JOIN playlist favorited_playlist
                  ON favorited_playlist.id = received_favorite.target_id
                 AND favorited_playlist.visibility = 'PUBLIC'
                WHERE favorited_playlist.user_id = u.id
                  AND received_favorite.target_type = 'PLAYLIST'
              ) AS received_favorite_count,
              (
                SELECT COUNT(*)
                FROM comment authored_comment
                WHERE authored_comment.user_id = u.id
                  AND authored_comment.status = 1
                  AND (
                    (
                      authored_comment.target_type = 'SONG'
                      AND EXISTS (
                        SELECT 1
                        FROM song public_song
                        WHERE public_song.id = authored_comment.target_id
                          AND public_song.status = 1
                      )
                    )
                    OR
                    (
                      authored_comment.target_type = 'PLAYLIST'
                      AND EXISTS (
                        SELECT 1
                        FROM playlist public_comment_playlist
                        WHERE public_comment_playlist.id = authored_comment.target_id
                          AND public_comment_playlist.visibility = 'PUBLIC'
                      )
                    )
                  )
              ) AS comment_count
            FROM `user` u
            WHERE u.status = 1
              AND LOWER(u.nickname) LIKE #{contains} ESCAPE '!'
            ORDER BY
              CASE
                WHEN LOWER(u.nickname) = #{exact} THEN 0
                WHEN LOWER(u.nickname) LIKE #{prefix} ESCAPE '!' THEN 1
                ELSE 2
              END,
              u.updated_at DESC,
              u.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<SearchUserProjection> searchUsers(
            @Param("exact") String exact,
            @Param("prefix") String prefix,
            @Param("contains") String contains,
            @Param("offset") long offset,
            @Param("limit") long limit
    );

    @Select("""
            SELECT COUNT(*)
            FROM `user` u
            WHERE u.status = 1
              AND LOWER(u.nickname) LIKE #{contains} ESCAPE '!'
            """)
    long countUsers(@Param("contains") String contains);
}
