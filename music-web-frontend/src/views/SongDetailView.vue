<template>
  <section>
    <PageToolbar />
    <EmptyState v-if="loading">正在加载歌曲...</EmptyState>
    <template v-else-if="song">
      <section class="playback-detail">
        <section class="detail-hero">
          <div class="detail-cover">
            <img v-if="song.coverUrl" :src="resolveMediaUrl(song.coverUrl)" alt="" />
            <span v-else>♪</span>
          </div>
          <div class="detail-copy">
            <p class="feature-label">歌曲</p>
            <h1 class="page-title">{{ song.title }}</h1>
            <p>{{ song.artistName || "未知歌手" }} · {{ song.albumTitle || "未知专辑" }}</p>
            <p class="muted-line">{{ song.genre || "Pop" }} · {{ song.mood || "MeloSpace" }} · {{ formatDuration(song.durationSeconds) }}</p>
            <div class="detail-actions">
              <button type="button" class="primary-action" @click="play">播放</button>
              <button type="button" class="secondary-action" @click="favorite">收藏</button>
            </div>
          </div>
        </section>

        <LyricPanel :song="song" :current-time="player.currentTime" :is-current-song="isCurrentSong" @seek="seekLyric" />
      </section>

      <section v-if="auth.isAuthenticated" class="compact-panel">
        <div class="section-head">
          <h2>加入歌单</h2>
          <span class="chevron">›</span>
        </div>
        <form class="inline-form" @submit.prevent="addToPlaylist">
          <select v-model.number="selectedPlaylistId">
            <option :value="0">选择我的歌单</option>
            <option v-for="playlist in myPlaylists" :key="playlist.id" :value="playlist.id">{{ playlist.title }}</option>
          </select>
          <button type="submit">添加</button>
        </form>
      </section>

      <section class="compact-panel">
        <div class="section-head">
          <h2>评论</h2>
          <span class="chevron">›</span>
        </div>
        <form v-if="auth.isAuthenticated" class="comment-form" @submit.prevent="submitComment">
          <textarea v-model.trim="commentText" placeholder="写一条评论" />
          <button type="submit">发布</button>
        </form>
        <div class="comment-list">
          <p v-for="comment in comments" :key="comment.id">{{ comment.content }}</p>
          <p v-if="!comments.length" class="muted-line">暂无评论。</p>
        </div>
      </section>
    </template>
    <EmptyState v-else>歌曲不存在或已下架。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { commentApi, favoriteApi, playlistApi, songApi, userApi } from "@/api";
import type { CommentItem, Playlist, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import LyricPanel from "@/components/LyricPanel.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import { useAuthStore } from "@/stores/auth";
import { usePlayerStore } from "@/stores/player";
import { useUiStore } from "@/stores/ui";
import { formatDuration, resolveMediaUrl } from "@/utils/format";

const route = useRoute();
const auth = useAuthStore();
const player = usePlayerStore();
const ui = useUiStore();
const loading = ref(true);
const song = ref<Song | null>(null);
const comments = ref<CommentItem[]>([]);
const myPlaylists = ref<Playlist[]>([]);
const selectedPlaylistId = ref(0);
const commentText = ref("");
const isCurrentSong = computed(() => Boolean(song.value && player.currentSong?.id === song.value.id));

onMounted(loadSong);

async function loadSong() {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    song.value = await songApi.detail(id);
    const commentPage = await commentApi.list("SONG", id, 1, 20);
    comments.value = commentPage.items;
    if (auth.isAuthenticated) {
      const playlistPage = await userApi.playlists(1, 50);
      myPlaylists.value = playlistPage.items;
    }
  } finally {
    loading.value = false;
  }
}

function play() {
  if (!song.value) return;
  player.playSong(song.value, [song.value]);
}

async function seekLyric(time: number) {
  if (!song.value) return;
  if (!isCurrentSong.value) {
    const played = await player.playSong(song.value, [song.value]);
    if (!played) return;
  }
  player.seekTo(time, true);
}

async function favorite() {
  if (!song.value) return;
  await favoriteApi.add("SONG", song.value.id);
  ui.toast("已收藏歌曲");
}

async function addToPlaylist() {
  if (!song.value || !selectedPlaylistId.value) return;
  await playlistApi.addSong(selectedPlaylistId.value, song.value.id);
  ui.toast("已加入歌单");
}

async function submitComment() {
  if (!song.value || !commentText.value) return;
  const created = await commentApi.create("SONG", song.value.id, commentText.value);
  comments.value.unshift(created);
  commentText.value = "";
}
</script>
