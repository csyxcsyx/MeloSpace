package com.musicweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.musicweb.common.ErrorCode;
import com.musicweb.common.PageResult;
import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.Playlist;
import com.musicweb.entity.PlaylistSong;
import com.musicweb.entity.Song;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.SearchMapper;
import com.musicweb.mapper.projection.SearchUserProjection;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import com.musicweb.service.PlaylistSongService;
import com.musicweb.service.SearchService;
import com.musicweb.support.MusicResponseAssembler;
import com.musicweb.vo.AlbumResponse;
import com.musicweb.vo.ArtistResponse;
import com.musicweb.vo.PlaylistResponse;
import com.musicweb.vo.PublicUserResponse;
import com.musicweb.vo.SearchResponse;
import com.musicweb.vo.SearchSuggestionResponse;
import com.musicweb.vo.SearchTotalsResponse;
import com.musicweb.vo.SongResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SearchServiceImpl implements SearchService {

    private static final int SUMMARY_SIZE = 10;
    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SUGGESTION_SIZE = 20;

    private final SearchMapper searchMapper;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final PlaylistSongService playlistSongService;

    public SearchServiceImpl(
            SearchMapper searchMapper,
            ArtistService artistService,
            AlbumService albumService,
            PlaylistSongService playlistSongService
    ) {
        this.searchMapper = searchMapper;
        this.artistService = artistService;
        this.albumService = albumService;
        this.playlistSongService = playlistSongService;
    }

    @Override
    public SearchResponse search(String keyword) {
        SearchQuery query = parseKeyword(keyword, false);
        if (query == null) {
            return new SearchResponse(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    SearchTotalsResponse.empty()
            );
        }

        PageResult<SongResponse> songs = searchSongs(query, 1, SUMMARY_SIZE);
        PageResult<ArtistResponse> artists = searchArtists(query, 1, SUMMARY_SIZE);
        PageResult<AlbumResponse> albums = searchAlbums(query, 1, SUMMARY_SIZE);
        PageResult<PlaylistResponse> playlists = searchPlaylists(query, 1, SUMMARY_SIZE);
        PageResult<PublicUserResponse> users = searchUsers(query, 1, SUMMARY_SIZE);
        return new SearchResponse(
                songs.items(),
                artists.items(),
                albums.items(),
                playlists.items(),
                users.items(),
                new SearchTotalsResponse(
                        songs.total(),
                        artists.total(),
                        albums.total(),
                        playlists.total(),
                        users.total()
                )
        );
    }

    @Override
    public List<SearchSuggestionResponse> suggestions(String keyword, int limit) {
        SearchQuery query = parseKeyword(keyword, true);
        if (limit < 1 || limit > MAX_SUGGESTION_SIZE) {
            throw parameterError("建议数量必须在 1 到 20 之间");
        }

        List<SongResponse> songs = toSongResponses(
                searchMapper.searchSongs(query.exact(), query.prefix(), query.contains(), 0, limit)
        );
        Map<Long, Artist> albumArtists;
        List<Album> albumEntities = searchMapper.searchAlbums(
                query.exact(), query.prefix(), query.contains(), 0, limit
        );
        albumArtists = loadArtistsByIds(
                albumEntities.stream().map(Album::getArtistId).collect(Collectors.toSet())
        );

        List<List<SearchSuggestionResponse>> buckets = List.of(
                songs.stream()
                        .map(song -> new SearchSuggestionResponse(
                                "SONG",
                                song.id(),
                                song.title(),
                                joinSubtitle(song.artistName(), song.albumTitle()),
                                song.coverUrl()
                        ))
                        .toList(),
                searchMapper.searchArtists(query.exact(), query.prefix(), query.contains(), 0, limit).stream()
                        .map(artist -> new SearchSuggestionResponse(
                                "ARTIST",
                                artist.getId(),
                                artist.getName(),
                                "歌手",
                                artist.getAvatarUrl()
                        ))
                        .toList(),
                albumEntities.stream()
                        .map(album -> new SearchSuggestionResponse(
                                "ALBUM",
                                album.getId(),
                                album.getTitle(),
                                artistName(album.getArtistId(), albumArtists),
                                album.getCoverUrl()
                        ))
                        .toList(),
                searchMapper.searchPlaylists(query.exact(), query.prefix(), query.contains(), 0, limit).stream()
                        .map(playlist -> new SearchSuggestionResponse(
                                "PLAYLIST",
                                playlist.getId(),
                                playlist.getTitle(),
                                "歌单",
                                playlist.getCoverUrl()
                        ))
                        .toList(),
                searchMapper.searchUsers(query.exact(), query.prefix(), query.contains(), 0, limit).stream()
                        .map(user -> new SearchSuggestionResponse(
                                "USER",
                                user.getId(),
                                user.getNickname(),
                                "用户",
                                user.getAvatarUrl()
                        ))
                        .toList()
        );

        List<SearchSuggestionResponse> suggestions = new ArrayList<>(limit);
        for (int index = 0; suggestions.size() < limit; index++) {
            boolean added = false;
            for (List<SearchSuggestionResponse> bucket : buckets) {
                if (index < bucket.size()) {
                    suggestions.add(bucket.get(index));
                    added = true;
                    if (suggestions.size() == limit) {
                        break;
                    }
                }
            }
            if (!added) {
                break;
            }
        }
        return suggestions;
    }

    @Override
    public PageResult<SongResponse> searchSongs(String keyword, long page, long size) {
        return searchSongs(parseKeyword(keyword, true), page, size);
    }

    @Override
    public PageResult<ArtistResponse> searchArtists(String keyword, long page, long size) {
        return searchArtists(parseKeyword(keyword, true), page, size);
    }

    @Override
    public PageResult<AlbumResponse> searchAlbums(String keyword, long page, long size) {
        return searchAlbums(parseKeyword(keyword, true), page, size);
    }

    @Override
    public PageResult<PlaylistResponse> searchPlaylists(String keyword, long page, long size) {
        return searchPlaylists(parseKeyword(keyword, true), page, size);
    }

    @Override
    public PageResult<PublicUserResponse> searchUsers(String keyword, long page, long size) {
        return searchUsers(parseKeyword(keyword, true), page, size);
    }

    private PageResult<SongResponse> searchSongs(SearchQuery query, long page, long size) {
        long offset = validatePageAndOffset(page, size);
        List<Song> songs = searchMapper.searchSongs(
                query.exact(), query.prefix(), query.contains(), offset, size
        );
        return new PageResult<>(
                toSongResponses(songs),
                page,
                size,
                searchMapper.countSongs(query.contains())
        );
    }

    private PageResult<ArtistResponse> searchArtists(SearchQuery query, long page, long size) {
        long offset = validatePageAndOffset(page, size);
        List<ArtistResponse> artists = searchMapper.searchArtists(
                        query.exact(), query.prefix(), query.contains(), offset, size
                ).stream()
                .map(MusicResponseAssembler::toArtistResponse)
                .toList();
        return new PageResult<>(artists, page, size, searchMapper.countArtists(query.contains()));
    }

    private PageResult<AlbumResponse> searchAlbums(SearchQuery query, long page, long size) {
        long offset = validatePageAndOffset(page, size);
        List<Album> albums = searchMapper.searchAlbums(
                query.exact(), query.prefix(), query.contains(), offset, size
        );
        Map<Long, Artist> artistsById = loadArtistsByIds(
                albums.stream().map(Album::getArtistId).collect(Collectors.toSet())
        );
        return new PageResult<>(
                albums.stream()
                        .map(album -> MusicResponseAssembler.toAlbumResponse(album, artistsById))
                        .toList(),
                page,
                size,
                searchMapper.countAlbums(query.contains())
        );
    }

    private PageResult<PlaylistResponse> searchPlaylists(SearchQuery query, long page, long size) {
        long offset = validatePageAndOffset(page, size);
        List<PlaylistResponse> playlists = searchMapper.searchPlaylists(
                        query.exact(), query.prefix(), query.contains(), offset, size
                ).stream()
                .map(this::toPlaylistResponse)
                .toList();
        return new PageResult<>(
                playlists,
                page,
                size,
                searchMapper.countPlaylists(query.contains())
        );
    }

    private PageResult<PublicUserResponse> searchUsers(SearchQuery query, long page, long size) {
        long offset = validatePageAndOffset(page, size);
        List<PublicUserResponse> users = searchMapper.searchUsers(
                        query.exact(), query.prefix(), query.contains(), offset, size
                ).stream()
                .map(this::toPublicUserResponse)
                .toList();
        return new PageResult<>(users, page, size, searchMapper.countUsers(query.contains()));
    }

    private SearchQuery parseKeyword(String keyword, boolean required) {
        String normalized = keyword == null ? "" : keyword.strip();
        if (normalized.isEmpty()) {
            if (required) {
                throw parameterError("搜索关键词不能为空");
            }
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_KEYWORD_LENGTH) {
            throw parameterError("搜索关键词不能超过 50 个字符");
        }

        String exact = normalized.toLowerCase(Locale.ROOT);
        String escaped = escapeLikePattern(exact);
        return new SearchQuery(exact, escaped + "%", "%" + escaped + "%");
    }

    private long validatePageAndOffset(long page, long size) {
        if (page < 1) {
            throw parameterError("页码必须从 1 开始");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw parameterError("每页数量必须在 1 到 100 之间");
        }
        try {
            return Math.multiplyExact(page - 1, size);
        } catch (ArithmeticException exception) {
            throw parameterError("页码超出允许范围");
        }
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private List<SongResponse> toSongResponses(List<Song> songs) {
        if (songs.isEmpty()) {
            return List.of();
        }
        Map<Long, Artist> artistsById = loadArtistsByIds(
                songs.stream().map(Song::getArtistId).collect(Collectors.toSet())
        );
        Map<Long, Album> albumsById = loadAlbumsByIds(
                songs.stream().map(Song::getAlbumId).filter(Objects::nonNull).collect(Collectors.toSet())
        );
        return songs.stream()
                .map(song -> MusicResponseAssembler.toSongResponse(song, artistsById, albumsById))
                .toList();
    }

    private Map<Long, Artist> loadArtistsByIds(Set<Long> artistIds) {
        if (artistIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return artistService.listByIds(artistIds).stream()
                .collect(Collectors.toMap(Artist::getId, Function.identity()));
    }

    private Map<Long, Album> loadAlbumsByIds(Set<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return albumService.listByIds(albumIds).stream()
                .collect(Collectors.toMap(Album::getId, Function.identity()));
    }

    private PlaylistResponse toPlaylistResponse(Playlist playlist) {
        return new PlaylistResponse(
                playlist.getId(),
                playlist.getUserId(),
                playlist.getTitle(),
                playlist.getDescription(),
                playlist.getCoverUrl(),
                playlist.getVisibility(),
                playlist.getPlayCount(),
                playlist.getFavoriteCount(),
                playlistSongService.count(new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlist.getId())),
                playlist.getCreatedAt(),
                playlist.getUpdatedAt(),
                null,
                null,
                List.of(),
                0L,
                false,
                false
        );
    }

    private PublicUserResponse toPublicUserResponse(SearchUserProjection user) {
        return new PublicUserResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio(),
                nullSafeCount(user.getPublicPlaylistCount()),
                nullSafeCount(user.getReceivedFavoriteCount()),
                nullSafeCount(user.getCommentCount())
        );
    }

    private long nullSafeCount(Long count) {
        return count == null ? 0 : count;
    }

    private String artistName(Long artistId, Map<Long, Artist> artistsById) {
        Artist artist = artistsById.get(artistId);
        return artist == null ? null : artist.getName();
    }

    private String joinSubtitle(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + " · " + second;
    }

    private BusinessException parameterError(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message, HttpStatus.BAD_REQUEST);
    }

    private record SearchQuery(String exact, String prefix, String contains) {
    }
}
