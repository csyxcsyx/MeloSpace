<template>
  <section>
    <EmptyState v-if="loading">正在加载歌单...</EmptyState>
    <template v-else-if="playlist">
      <section class="detail-hero">
        <div class="detail-cover">
          <img v-if="playlist.coverUrl" :src="resolveMediaUrl(playlist.coverUrl)" alt="" />
          <span v-else>♬</span>
        </div>
        <div class="detail-copy">
          <p class="feature-label">歌单</p>
          <h1 class="page-title">{{ playlist.title }}</h1>
          <p>{{ playlist.description || "这个歌单还没有描述。" }}</p>
          <p class="muted-line">{{ playlist.songs.length }} 首 · {{ playlist.visibility === "PUBLIC" ? "公开" : "私有" }}</p>
          <div class="detail-actions">
            <button type="button" class="primary-action" @click="playAll">播放全部</button>
            <button type="button" class="secondary-action" @click="favorite">收藏</button>
          </div>
        </div>
      </section>

      <section>
        <div class="section-head">
          <h2>歌曲</h2>
          <span class="chevron">›</span>
        </div>
        <SongColumnList v-if="songs.length" :songs="songs" @play="playSong" />
        <EmptyState v-else>歌单还没有歌曲。</EmptyState>
      </section>
    </template>
    <EmptyState v-else>歌单不存在或没有访问权限。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { favoriteApi, playlistApi } from "@/api";
import type { PlaylistDetail, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { usePlayerStore } from "@/stores/player";
import { useUiStore } from "@/stores/ui";
import { resolveMediaUrl } from "@/utils/format";

const route = useRoute();
const player = usePlayerStore();
const ui = useUiStore();
const loading = ref(true);
const playlist = ref<PlaylistDetail | null>(null);
const songs = computed(() => playlist.value?.songs.map((item) => item.song) ?? []);

onMounted(loadPlaylist);

async function loadPlaylist() {
  loading.value = true;
  try {
    playlist.value = await playlistApi.detail(Number(route.params.id));
  } finally {
    loading.value = false;
  }
}

function playSong(song: Song) {
  player.playSong(song, songs.value);
}

function playAll() {
  if (!songs.value.length) return;
  player.playSong(songs.value[0], songs.value);
}

async function favorite() {
  if (!playlist.value) return;
  await favoriteApi.add("PLAYLIST", playlist.value.id);
  ui.toast("已收藏歌单");
}
</script>
