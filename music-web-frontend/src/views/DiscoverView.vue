<template>
  <section>
    <h1 class="page-title">新发现</h1>

    <EmptyState v-if="loading">正在加载音乐内容...</EmptyState>
    <template v-else>
      <div v-if="recommendedAlbum || recommendedArtist" class="discover-feature-grid">
        <button
          v-if="recommendedAlbum"
          class="recommendation-card album-recommendation"
          type="button"
          @click="openAlbum(recommendedAlbum.id)"
        >
          <img v-if="recommendedAlbum.coverUrl" :src="resolveMediaUrl(recommendedAlbum.coverUrl)" alt="" />
          <div class="recommendation-copy">
            <span>随机专辑</span>
            <strong>{{ recommendedAlbum.title }}</strong>
            <small>{{ recommendedAlbum.artistName || "MeloSpace" }} · {{ albumSongCount }} 首歌</small>
          </div>
        </button>

        <button
          v-if="recommendedArtist"
          class="recommendation-card artist-recommendation"
          type="button"
          @click="openArtist(recommendedArtist.id)"
        >
          <img v-if="recommendedArtistImage" :src="resolveMediaUrl(recommendedArtistImage)" alt="" />
          <div class="recommendation-copy">
            <span>随机歌手</span>
            <strong>{{ recommendedArtist.name }}</strong>
            <small>{{ artistSongCount }} 首歌 · {{ artistAlbumCount }} 张专辑</small>
          </div>
        </button>
      </div>

      <section>
        <div class="section-head">
          <h2>推荐歌曲</h2>
        </div>
        <SongColumnList
          v-if="recommendedSongs.length"
          :songs="recommendedSongs"
          :column-count="3"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else>还没有可展示的歌曲。</EmptyState>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { albumApi, artistApi, songApi } from "@/api";
import type { Album, Artist, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

const player = usePlayerStore();
const router = useRouter();
const loading = ref(true);
const songs = ref<Song[]>([]);
const albums = ref<Album[]>([]);
const artists = ref<Artist[]>([]);
const recommendedAlbum = ref<Album | null>(null);
const recommendedArtist = ref<Artist | null>(null);

const recommendedSongs = computed(() => pickDiverseSongs(songs.value, 12));
const albumSongCount = computed(() => {
  if (!recommendedAlbum.value) return 0;
  return songs.value.filter((song) => song.albumId === recommendedAlbum.value?.id).length;
});
const artistSongCount = computed(() => {
  if (!recommendedArtist.value) return 0;
  return songs.value.filter((song) => song.artistId === recommendedArtist.value?.id).length;
});
const artistAlbumCount = computed(() => {
  if (!recommendedArtist.value) return 0;
  return albums.value.filter((album) => album.artistId === recommendedArtist.value?.id).length;
});
const recommendedArtistImage = computed(() => {
  if (!recommendedArtist.value) return "";
  return recommendedArtist.value.avatarUrl
    || songs.value.find((song) => song.artistId === recommendedArtist.value?.id)?.coverUrl
    || albums.value.find((album) => album.artistId === recommendedArtist.value?.id)?.coverUrl
    || "";
});

onMounted(loadDiscover);

async function loadDiscover() {
  loading.value = true;
  try {
    const [songPage, albumList, artistList] = await Promise.all([
      songApi.list({ page: 1, size: 100 }),
      albumApi.list(),
      artistApi.list()
    ]);
    songs.value = songPage.items;
    albums.value = albumList;
    artists.value = artistList;
    recommendedAlbum.value = randomItem(albumList.filter((album) => album.coverUrl));
    recommendedArtist.value = randomItem(artistList);
  } finally {
    loading.value = false;
  }
}

function toggleSongPlayback(song: Song) {
  if (player.currentSong?.id === song.id) {
    if (player.isPlaying) {
      player.setPlaying(false);
    } else {
      void player.resumeCurrent();
    }
    return;
  }
  player.playSong(song, recommendedSongs.value);
}

async function openPlayer(song: Song) {
  const played = await player.playSong(song, recommendedSongs.value);
  if (played) router.push("/player");
}

function openAlbum(id: number) {
  router.push(`/albums/${id}`);
}

function openArtist(id: number) {
  router.push(`/artists/${id}`);
}

function randomItem<T>(items: T[]) {
  if (!items.length) return null;
  return items[Math.floor(Math.random() * items.length)];
}

function pickDiverseSongs(source: Song[], limit: number) {
  const selected: Song[] = [];
  const usedArtists = new Set<number>();
  for (const song of source) {
    if (selected.length >= limit) break;
    if (usedArtists.has(song.artistId)) continue;
    selected.push(song);
    usedArtists.add(song.artistId);
  }
  for (const song of source) {
    if (selected.length >= limit) break;
    if (selected.some((item) => item.id === song.id)) continue;
    selected.push(song);
  }
  return selected;
}
</script>
