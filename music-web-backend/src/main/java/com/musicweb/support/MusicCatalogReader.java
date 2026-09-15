package com.musicweb.support;

import com.musicweb.entity.Album;
import com.musicweb.entity.Artist;
import com.musicweb.service.AlbumService;
import com.musicweb.service.ArtistService;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MusicCatalogReader {

    private final ArtistService artistService;
    private final AlbumService albumService;

    public MusicCatalogReader(ArtistService artistService, AlbumService albumService) {
        this.artistService = artistService;
        this.albumService = albumService;
    }

    public Map<Long, Artist> artistsById(Set<Long> artistIds) {
        if (artistIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return artistService.listByIds(artistIds).stream()
                .collect(Collectors.toMap(Artist::getId, Function.identity()));
    }

    public Map<Long, Album> albumsById(Set<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return albumService.listByIds(albumIds).stream()
                .collect(Collectors.toMap(Album::getId, Function.identity()));
    }
}
