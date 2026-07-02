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
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Disc3, Play } from "lucide-vue-next";
import { albumApi, songApi } from "@/api";
import type { Album, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
const loading = ref(true);
const album = ref<Album | null>(null);
const songs = ref<Song[]>([]);

onMounted(loadAlbum);

watch(() => route.params.id, loadAlbum);

async function loadAlbum() {
  const albumId = Number(route.params.id);
  if (!Number.isFinite(albumId)) {
    album.value = null;
    songs.value = [];
    loading.value = false;
    return;
  }

  loading.value = true;
  try {
    const [albumList, songPage] = await Promise.all([
      albumApi.list(),
      songApi.list({ page: 1, size: 100, albumId })
    ]);
    album.value = albumList.find((item) => item.id === albumId) ?? null;
    songs.value = songPage.items;
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
