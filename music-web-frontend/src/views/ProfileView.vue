<template>
  <section>
    <h1 class="page-title">我的音乐</h1>

    <section class="profile-grid">
      <div class="compact-panel">
        <div class="section-head">
          <h2>我的歌单</h2>
          <span class="chevron">›</span>
        </div>
        <form class="inline-form" @submit.prevent="createPlaylist">
          <input v-model.trim="playlistTitle" placeholder="新歌单名称" />
          <button type="submit">创建</button>
        </form>
        <div class="profile-list">
          <RouterLink v-for="playlist in playlists" :key="playlist.id" :to="`/playlists/${playlist.id}`">
            {{ playlist.title }} <span>{{ playlist.songCount }} 首</span>
          </RouterLink>
          <p v-if="!playlists.length" class="muted-line">还没有创建歌单。</p>
        </div>
      </div>

      <div class="compact-panel">
        <div class="section-head">
          <h2>收藏</h2>
          <span class="chevron">›</span>
        </div>
        <div class="profile-list">
          <p v-for="favorite in favorites" :key="favorite.id">{{ favorite.targetType }} #{{ favorite.targetId }}</p>
          <p v-if="!favorites.length" class="muted-line">还没有收藏内容。</p>
        </div>
      </div>

      <div class="compact-panel">
        <div class="section-head">
          <h2>最近播放</h2>
          <span class="chevron">›</span>
        </div>
        <div class="profile-list">
          <p v-for="item in recent" :key="item.id">歌曲 #{{ item.songId }} · {{ item.sourceType || "FRONTEND" }}</p>
          <p v-if="!recent.length" class="muted-line">暂无最近播放。</p>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { playlistApi, userApi } from "@/api";
import type { FavoriteItem, PlayHistoryItem, Playlist } from "@/api/types";
import { useUiStore } from "@/stores/ui";

const ui = useUiStore();
const playlists = ref<Playlist[]>([]);
const favorites = ref<FavoriteItem[]>([]);
const recent = ref<PlayHistoryItem[]>([]);
const playlistTitle = ref("");

onMounted(loadProfile);

async function loadProfile() {
  const [playlistPage, favoritePage, recentPage] = await Promise.all([
    userApi.playlists(1, 20),
    userApi.favorites(1, 20),
    userApi.recentPlays(1, 20)
  ]);
  playlists.value = playlistPage.items;
  favorites.value = favoritePage.items;
  recent.value = recentPage.items;
}

async function createPlaylist() {
  if (!playlistTitle.value) return;
  await playlistApi.create({ title: playlistTitle.value, visibility: "PUBLIC" });
  playlistTitle.value = "";
  ui.toast("歌单已创建");
  await loadProfile();
}
</script>
