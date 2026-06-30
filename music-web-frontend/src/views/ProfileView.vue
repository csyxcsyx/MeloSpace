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
          <RouterLink v-for="favorite in favorites" :key="favorite.id" :to="favoritePath(favorite)">
            <strong>{{ favoriteTitle(favorite) }}</strong>
            <span>{{ favoriteSubtitle(favorite) }}</span>
          </RouterLink>
          <p v-if="!favorites.length" class="muted-line">还没有收藏内容。</p>
        </div>
      </div>

      <div class="compact-panel">
        <div class="section-head">
          <h2>最近播放</h2>
          <span class="chevron">›</span>
        </div>
        <div class="profile-list">
          <RouterLink v-for="item in recent" :key="item.id" :to="`/songs/${item.song?.id || item.songId}`">
            <strong>{{ item.song?.title || `歌曲 #${item.songId}` }}</strong>
            <span>{{ item.song?.artistName || item.sourceType || "MeloSpace" }}</span>
          </RouterLink>
          <p v-if="!recent.length" class="muted-line">暂无最近播放。</p>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { playlistApi, songApi, userApi } from "@/api";
import type { FavoriteItem, PlayHistoryItem, Playlist, Song } from "@/api/types";
import { useUiStore } from "@/stores/ui";

interface FavoriteDisplayItem extends FavoriteItem {
  song?: Song | null;
  playlist?: Playlist | null;
}

interface RecentDisplayItem extends PlayHistoryItem {
  song?: Song | null;
}

const ui = useUiStore();
const playlists = ref<Playlist[]>([]);
const favorites = ref<FavoriteDisplayItem[]>([]);
const recent = ref<RecentDisplayItem[]>([]);
const playlistTitle = ref("");

onMounted(loadProfile);

async function loadProfile() {
  const [playlistPage, favoritePage, recentPage] = await Promise.all([
    userApi.playlists(1, 20),
    userApi.favorites(1, 20),
    userApi.recentPlays(1, 20)
  ]);
  playlists.value = playlistPage.items;
  favorites.value = await hydrateFavorites(favoritePage.items);
  recent.value = await hydrateRecentPlays(recentPage.items);
}

async function hydrateFavorites(items: FavoriteItem[]): Promise<FavoriteDisplayItem[]> {
  return Promise.all(
    items.map(async (favorite) => {
      if (favorite.targetType === "SONG" && !favorite.song) {
        try {
          return { ...favorite, song: await songApi.detail(favorite.targetId) };
        } catch {
          return favorite;
        }
      }
      if (favorite.targetType === "PLAYLIST" && !favorite.playlist) {
        try {
          return { ...favorite, playlist: await playlistApi.detail(favorite.targetId) };
        } catch {
          return favorite;
        }
      }
      return favorite;
    })
  );
}

async function hydrateRecentPlays(items: PlayHistoryItem[]): Promise<RecentDisplayItem[]> {
  return Promise.all(
    items.map(async (item) => {
      if (item.song) {
        return item;
      }
      try {
        return { ...item, song: await songApi.detail(item.songId) };
      } catch {
        return item;
      }
    })
  );
}

function favoritePath(favorite: FavoriteDisplayItem) {
  if (favorite.targetType === "PLAYLIST") {
    return `/playlists/${favorite.playlist?.id || favorite.targetId}`;
  }
  return `/songs/${favorite.song?.id || favorite.targetId}`;
}

function favoriteTitle(favorite: FavoriteDisplayItem) {
  if (favorite.targetType === "PLAYLIST") {
    return favorite.playlist?.title || `歌单 #${favorite.targetId}`;
  }
  return favorite.song?.title || `歌曲 #${favorite.targetId}`;
}

function favoriteSubtitle(favorite: FavoriteDisplayItem) {
  if (favorite.targetType === "PLAYLIST") {
    return favorite.playlist ? `${favorite.playlist.songCount} 首` : "歌单";
  }
  return favorite.song?.artistName || "歌曲";
}

async function createPlaylist() {
  if (!playlistTitle.value) return;
  await playlistApi.create({ title: playlistTitle.value, visibility: "PUBLIC" });
  playlistTitle.value = "";
  ui.toast("歌单已创建");
  await loadProfile();
}
</script>
