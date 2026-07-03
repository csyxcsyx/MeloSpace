<template>
  <section>
    <PageToolbar />
    <EmptyState v-if="loading">正在加载专辑...</EmptyState>
    <template v-else-if="album">
      <header class="catalog-hero">
        <div class="catalog-cover square-cover">
          <img v-if="album.coverUrl" :src="resolveMediaUrl(album.coverUrl)" alt="" />
          <Disc3 v-else :size="44" />
        </div>
        <div class="catalog-copy">
          <p class="feature-label">专辑</p>
          <h1>{{ album.title }}</h1>
          <p>{{ album.artistName || "未知歌手" }}</p>
          <p class="catalog-meta">{{ album.releaseDate || "发行日期未录入" }} · {{ songs.length }} 首歌</p>
          <div class="detail-actions">
            <button class="primary-action" type="button" :disabled="!songs.length" @click="playAll">
              <Play :size="17" fill="currentColor" />
              播放全部
            </button>
          </div>
        </div>
      </header>

      <section>
        <div class="section-head">
          <h2>歌曲</h2>
        </div>
        <SongColumnList
          v-if="songs.length"
          :songs="songs"
          :column-count="2"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else>这张专辑还没有歌曲。</EmptyState>
      </section>
    </template>
    <EmptyState v-else>没有找到这张专辑。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { onActivated, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Disc3, Play } from "lucide-vue-next";
import type { Album, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { useCatalogCacheStore } from "@/stores/catalogCache";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
const catalogCache = useCatalogCacheStore();
const loading = ref(true);
const album = ref<Album | null>(null);
const songs = ref<Song[]>([]);
const loadedAlbumId = ref<number | null>(null);

onMounted(loadAlbum);
onActivated(loadAlbum);

watch(
  () => [route.name, route.params.id],
  () => {
    if (route.name === "album-detail") {
      void loadAlbum();
    }
  }
);

async function loadAlbum() {
  if (route.name !== "album-detail") return;
  const albumId = Number(route.params.id);
  if (!Number.isFinite(albumId)) {
    album.value = null;
    songs.value = [];
    loading.value = false;
    return;
  }

  if (loadedAlbumId.value === albumId && album.value) {
    loading.value = false;
    return;
  }

  const cached = catalogCache.getAlbumDetail(albumId);
  if (cached) {
    album.value = cached.album;
    songs.value = cached.songs;
    loadedAlbumId.value = albumId;
    loading.value = false;
    return;
  }

  loading.value = !album.value;
  try {
    const detail = await catalogCache.loadAlbumDetail(albumId);
    album.value = detail.album;
    songs.value = detail.songs;
    loadedAlbumId.value = albumId;
  } finally {
    loading.value = false;
  }
}

function playAll() {
  if (!songs.value.length) return;
  player.playSong(songs.value[0], songs.value);
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
  player.playSong(song, songs.value);
}

async function openPlayer(song: Song) {
  const played = await player.playSong(song, songs.value);
  if (played) router.push("/player");
}
</script>
