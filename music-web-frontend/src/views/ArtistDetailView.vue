<template>
  <section>
    <div class="page-actions">
      <button class="secondary-action compact-action" type="button" @click="goBack">
        <ArrowLeft :size="16" />
        返回
      </button>
      <RouterLink class="secondary-action compact-action" to="/discover">
        <Home :size="16" />
        首页
      </RouterLink>
    </div>

    <EmptyState v-if="loading">正在加载歌手...</EmptyState>
    <template v-else-if="artist">
      <section class="detail-hero artist-hero">
        <div class="detail-cover artist-avatar">
          <img v-if="artist.avatarUrl" :src="resolveMediaUrl(artist.avatarUrl)" alt="" />
          <MicVocal v-else :size="52" />
        </div>
        <div class="detail-copy">
          <p class="feature-label">歌手</p>
          <h1 class="page-title">{{ artist.name }}</h1>
          <p>{{ artist.bio || "这个歌手还没有简介。" }}</p>
          <p class="muted-line">{{ songs.length }} 首已上架歌曲</p>
          <div class="detail-actions">
            <button class="primary-action" type="button" :disabled="!songs.length" @click="playAll">
              <Play :size="16" fill="currentColor" />
              播放全部
            </button>
            <RouterLink class="secondary-action" to="/discover">返回发现</RouterLink>
          </div>
        </div>
      </section>

      <section>
        <div class="section-head">
          <h2>全部歌曲</h2>
          <span class="chevron">›</span>
        </div>
        <SongColumnList
          v-if="songs.length"
          :songs="songs"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else>该歌手暂无已上架歌曲。</EmptyState>
      </section>
    </template>
    <EmptyState v-else>歌手不存在。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { ArrowLeft, Home, MicVocal, Play } from "lucide-vue-next";
import { artistApi, songApi } from "@/api";
import type { Artist, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
const loading = ref(true);
const artist = ref<Artist | null>(null);
const songs = ref<Song[]>([]);

onMounted(loadArtist);

watch(
  () => route.params.id,
  () => {
    loadArtist();
  }
);

async function loadArtist() {
  const artistId = Number(route.params.id);
  if (!Number.isFinite(artistId)) return;

  loading.value = true;
  artist.value = null;
  songs.value = [];
  try {
    const [artistDetail, songPage] = await Promise.all([
      artistApi.detail(artistId),
      songApi.list({ page: 1, size: 100, artistId })
    ]);
    artist.value = artistDetail;
    songs.value = songPage.items;
  } finally {
    loading.value = false;
  }
}

function playSong(song: Song) {
  player.playSong(song, songs.value);
}

function toggleSongPlayback(song: Song) {
  if (player.currentSong?.id === song.id) {
    player.setPlaying(!player.isPlaying);
    return;
  }
  player.playSong(song, songs.value);
}

function openPlayer(song: Song) {
  player.playSong(song, songs.value);
  router.push("/player");
}

function playAll() {
  if (!songs.value.length) return;
  player.playSong(songs.value[0], songs.value);
}

function goBack() {
  if (window.history.state?.back) {
    router.back();
  } else {
    router.push("/discover");
  }
}
</script>
