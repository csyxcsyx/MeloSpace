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
          <div v-if="result.artists.length" class="result-list">
            <RouterLink v-for="artist in result.artists" :key="artist.id" class="result-link" :to="`/artists/${artist.id}`">
              <span class="result-thumb">
                <img v-if="artist.avatarUrl" :src="resolveMediaUrl(artist.avatarUrl)" alt="" />
                <UserRound v-else :size="18" />
              </span>
              <span>
                <strong>{{ artist.name }}</strong>
                <small>{{ artist.bio || "歌手主页" }}</small>
              </span>
            </RouterLink>
          </div>
          <p v-if="!result.artists.length" class="muted-line">暂无歌手结果</p>
        </div>
        <div class="result-panel">
          <h2>专辑</h2>
          <div v-if="result.albums.length" class="result-list">
            <RouterLink v-for="album in result.albums" :key="album.id" class="result-link" :to="`/albums/${album.id}`">
              <span class="result-thumb">
                <img v-if="album.coverUrl" :src="resolveMediaUrl(album.coverUrl)" alt="" />
                <Disc3 v-else :size="18" />
              </span>
              <span>
                <strong>{{ album.title }}</strong>
                <small>{{ album.artistName || "专辑主页" }}</small>
              </span>
            </RouterLink>
          </div>
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
import { Disc3, UserRound } from "lucide-vue-next";
import { searchApi } from "@/api";
import type { SearchResponse, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import PlaylistSection from "@/components/PlaylistSection.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
const keywordInput = ref(String(route.query.keyword || ""));
const result = ref<SearchResponse | null>(null);
const loading = ref(false);
const SEARCH_CACHE_LIMIT = 30;
const searchResultCache = new Map<string, SearchResponse>();
const searchRequestCache = new Map<string, Promise<SearchResponse>>();
let searchRunId = 0;

watch(
  () => (route.name === "search" ? String(route.query.keyword || "") : null),
  (keyword) => {
    if (keyword === null) return;
    keywordInput.value = keyword;
    void search();
  },
  { immediate: true }
);

function submit() {
  const keyword = keywordInput.value.trim();
  router.push({ path: "/search", query: keyword ? { keyword } : undefined });
}

async function search() {
  const keyword = keywordInput.value.trim();
  const runId = ++searchRunId;
  if (!keyword) {
    result.value = null;
    loading.value = false;
    return;
  }

  const cached = readCachedSearch(keyword);
  if (cached) {
    result.value = cached;
    loading.value = false;
    return;
  }

  loading.value = true;
  try {
    const data = await loadSearchResult(keyword);
    if (runId === searchRunId && keywordInput.value.trim() === keyword) {
      result.value = data;
    }
  } finally {
    if (runId === searchRunId) loading.value = false;
  }
}

function readCachedSearch(keyword: string) {
  const cached = searchResultCache.get(keyword);
  if (!cached) return null;
  searchResultCache.delete(keyword);
  searchResultCache.set(keyword, cached);
  return cached;
}

function rememberSearch(keyword: string, data: SearchResponse) {
  if (searchResultCache.has(keyword)) searchResultCache.delete(keyword);
  searchResultCache.set(keyword, data);
  while (searchResultCache.size > SEARCH_CACHE_LIMIT) {
    const oldestKey = searchResultCache.keys().next().value;
    if (!oldestKey) break;
    searchResultCache.delete(oldestKey);
  }
}

async function loadSearchResult(keyword: string) {
  const pending = searchRequestCache.get(keyword);
  if (pending) return pending;
  const request = searchApi.all(keyword)
    .then((data) => {
      rememberSearch(keyword, data);
      return data;
    })
    .finally(() => {
      searchRequestCache.delete(keyword);
    });
  searchRequestCache.set(keyword, request);
  return request;
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

async function openPlayer(song: Song) {
  const played = await player.playSong(song, result.value?.songs ?? [song]);
  if (played) router.push("/player");
}
</script>
