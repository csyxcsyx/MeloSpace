import { defineStore } from "pinia";
import { ref } from "vue";
import { albumApi, artistApi, songApi } from "@/api";
import type { Album, Artist, Song } from "@/api/types";

interface AlbumDetailCache {
  album: Album | null;
  songs: Song[];
  loadedAt: number;
}

interface ArtistDetailCache {
  artist: Artist | null;
  albums: Album[];
  songs: Song[];
  loadedAt: number;
}

const CACHE_TTL_MS = 10 * 60 * 1000;

function isFresh(loadedAt: number) {
  return Date.now() - loadedAt < CACHE_TTL_MS;
}

export const useCatalogCacheStore = defineStore("catalog-cache", () => {
  const albumDetails = ref<Record<number, AlbumDetailCache>>({});
  const artistDetails = ref<Record<number, ArtistDetailCache>>({});
  const albumRequests = new Map<number, Promise<AlbumDetailCache>>();
  const artistRequests = new Map<number, Promise<ArtistDetailCache>>();

  function getAlbumDetail(albumId: number) {
    const cached = albumDetails.value[albumId];
    return cached && isFresh(cached.loadedAt) ? cached : null;
  }

  function getArtistDetail(artistId: number) {
    const cached = artistDetails.value[artistId];
    return cached && isFresh(cached.loadedAt) ? cached : null;
  }

  async function loadAlbumDetail(albumId: number, force = false) {
    if (!force) {
      const cached = getAlbumDetail(albumId);
      if (cached) return cached;
      const pending = albumRequests.get(albumId);
      if (pending) return pending;
    }

    const request = Promise.all([
      albumApi.list(),
      songApi.list({ page: 1, size: 100, albumId })
    ])
      .then(([albumList, songPage]) => {
        const detail: AlbumDetailCache = {
          album: albumList.find((item) => item.id === albumId) ?? null,
          songs: songPage.items,
          loadedAt: Date.now()
        };
        albumDetails.value = { ...albumDetails.value, [albumId]: detail };
        return detail;
      })
      .finally(() => {
        albumRequests.delete(albumId);
      });

    albumRequests.set(albumId, request);
    return request;
  }

  async function loadArtistDetail(artistId: number, force = false) {
    if (!force) {
      const cached = getArtistDetail(artistId);
      if (cached) return cached;
      const pending = artistRequests.get(artistId);
      if (pending) return pending;
    }

    const request = Promise.all([
      artistApi.list(),
      albumApi.list({ artistId }),
      songApi.list({ page: 1, size: 100, artistId })
    ])
      .then(([artistList, albumList, songPage]) => {
        const detail: ArtistDetailCache = {
          artist: artistList.find((item) => item.id === artistId) ?? null,
          albums: albumList,
          songs: songPage.items,
          loadedAt: Date.now()
        };
        artistDetails.value = { ...artistDetails.value, [artistId]: detail };
        return detail;
      })
      .finally(() => {
        artistRequests.delete(artistId);
      });

    artistRequests.set(artistId, request);
    return request;
  }

  function invalidateAlbum(albumId?: number) {
    if (albumId == null) {
      albumDetails.value = {};
      return;
    }
    const { [albumId]: _removed, ...rest } = albumDetails.value;
    albumDetails.value = rest;
  }

  function invalidateArtist(artistId?: number) {
    if (artistId == null) {
      artistDetails.value = {};
      return;
    }
    const { [artistId]: _removed, ...rest } = artistDetails.value;
    artistDetails.value = rest;
  }

  return {
    getAlbumDetail,
    getArtistDetail,
    loadAlbumDetail,
    loadArtistDetail,
    invalidateAlbum,
    invalidateArtist
  };
});
