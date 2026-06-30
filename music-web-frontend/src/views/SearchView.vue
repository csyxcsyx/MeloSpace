<template>
  <section>
    <PageToolbar />
    <h1 class="page-title">搜索</h1>

    <form class="search-page-form" @submit.prevent="submit">
      <input v-model.trim="keywordInput" placeholder="搜索歌曲、歌手、专辑或歌单" />
      <button type="submit">搜索</button>
    </form>

    <EmptyState v-if="loading">正在搜索...</EmptyState>
    <template v-else-if="result">
      <section>
        <div class="section-head">
          <h2>歌曲</h2>
          <span class="chevron">›</span>
        </div>
        <SongColumnList
          v-if="result.songs.length"
          :songs="result.songs"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else>没有找到歌曲。</EmptyState>
      </section>

      <PlaylistSection v-if="result.playlists.length" title="歌单" :playlists="result.playlists" />

      <section class="result-grid">
        <div class="result-panel">
          <h2>歌手</h2>
          <p v-for="artist in result.artists" :key="artist.id">{{ artist.name }}</p>
          <p v-if="!result.artists.length" class="muted-line">暂无歌手结果</p>
        </div>
        <div class="result-panel">
          <h2>专辑</h2>
          <p v-for="album in result.albums" :key="album.id">{{ album.title }}</p>
          <p v-if="!result.albums.length" class="muted-line">暂无专辑结果</p>
        </div>
      </section>
    </template>
    <EmptyState v-else>输入关键词开始搜索。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { searchApi } from "@/api";
import type { SearchResponse, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import PlaylistSection from "@/components/PlaylistSection.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { usePlayerStore } from "@/stores/player";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
const keywordInput = ref(String(route.query.keyword || ""));
const result = ref<SearchResponse | null>(null);
const loading = ref(false);

watch(
  () => route.query.keyword,
  (keyword) => {
    keywordInput.value = String(keyword || "");
    search();
  },
  { immediate: true }
);

function submit() {
  router.push({ path: "/search", query: keywordInput.value ? { keyword: keywordInput.value } : undefined });
}

async function search() {
  if (!keywordInput.value) {
    result.value = null;
    return;
  }
  loading.value = true;
  try {
    result.value = await searchApi.all(keywordInput.value);
  } finally {
    loading.value = false;
  }
}

function playSong(song: Song) {
  player.playSong(song, result.value?.songs ?? [song]);
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
  player.playSong(song, result.value?.songs ?? [song]);
}

function openPlayer(song: Song) {
  player.playSong(song, result.value?.songs ?? [song]);
  router.push("/player");
}
</script>
