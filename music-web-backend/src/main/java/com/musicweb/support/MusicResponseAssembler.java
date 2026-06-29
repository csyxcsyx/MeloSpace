package com.musicweb.support;

import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.entity.Song;
import com.musicweb.vo.AlbumResponse;
import com.musicweb.vo.ArtistResponse;
import com.musicweb.vo.SongResponse;
import java.util.Map;

public final class MusicResponseAssembler {

    private MusicResponseAssembler() {
    }

    public static ArtistResponse toArtistResponse(Artist artist) {
        return new ArtistResponse(
                artist.getId(),
                artist.getName(),
                artist.getBio(),
                artist.getAvatarUrl(),
                artist.getCreatedAt(),
                artist.getUpdatedAt()
        );
    }

    public static AlbumResponse toAlbumResponse(Album album, Map<Long, Artist> artistsById) {
        Artist artist = artistsById.get(album.getArtistId());
        return new AlbumResponse(
                album.getId(),
                album.getTitle(),
                album.getArtistId(),
                artist == null ? null : artist.getName(),
                album.getCoverUrl(),
                album.getReleaseDate(),
                album.getCreatedAt(),
                album.getUpdatedAt()
        );
    }

    public static SongResponse toSongResponse(
            Song song,
            Map<Long, Artist> artistsById,
            Map<Long, Album> albumsById
    ) {
        Artist artist = artistsById.get(song.getArtistId());
        Album album = song.getAlbumId() == null ? null : albumsById.get(song.getAlbumId());
        return new SongResponse(
                song.getId(),
                song.getTitle(),
                song.getArtistId(),
                artist == null ? null : artist.getName(),
                song.getAlbumId(),
                album == null ? null : album.getTitle(),
                song.getCoverUrl(),
                song.getAudioUrl(),
                song.getLyricUrl(),
                song.getDurationSeconds(),
                song.getLanguage(),
                song.getGenre(),
                song.getMood(),
                song.getPlayCount(),
                song.getStatus(),
                song.getCreatedAt(),
                song.getUpdatedAt()
        );
    }
}
