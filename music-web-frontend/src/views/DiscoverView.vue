<template>
  <section>
    <h1 class="page-title">新发现</h1>

    <EmptyState v-if="loading">正在加载音乐内容...</EmptyState>
    <template v-else>
      <FeatureScroller v-if="songs.length || playlists.length">
        <article class="feature-unit">
          <p class="feature-label">推荐歌单</p>
          <h2 class="feature-title">{{ playlists[0]?.title || "橙音代表作" }}</h2>
          <p class="feature-subtitle">Orange Music 国语流行</p>
          <button class="feature-card feature-card-a" type="button" @click="playFirst">
            <div class="feature-art-text">
              <strong>{{ songs[0]?.title || "代表作" }}</strong>
              <span>{{ songs[0]?.artistName || "Orange Music" }}</span>
            </div>
            <div class="feature-caption">从项目曲库里挑选适合开场演示的歌曲。</div>
          </button>
        </article>

        <article class="feature-unit">
          <p class="feature-label">歌曲已更新</p>
          <h2 class="feature-title">今日热门</h2>
          <p class="feature-subtitle">Orange Music 热门</p>
          <button class="feature-card feature-card-b" type="button" @click="playSong(songs[1] || songs[0])">
            <div class="feature-art-text">
              <strong>{{ songs[1]?.title || songs[0]?.title || "今日热门" }}</strong>
              <span>{{ songs[1]?.mood || songs[0]?.mood || "热门播放" }}</span>
            </div>
            <div class="feature-caption">根据播放、收藏和评论构成的课程项目热门入口。</div>
          </button>
        </article>

        <article class="feature-unit">
          <p class="feature-label">排行榜更新</p>
          <h2 class="feature-title">每周热门 100 首：中国大陆</h2>
          <p class="feature-subtitle">Orange Music</p>
          <div class="feature-card chart-card">
            <p class="chart-title">每周热门 100 首</p>
            <p class="chart-region">中国大陆</p>
            <div class="heat-grid" aria-hidden="true">
              <span v-for="index in 24" :key="index" />
            </div>
            <div class="feature-caption">追踪每周热单，展示推荐和榜单能力。</div>
          </div>
        </article>
      </FeatureScroller>

      <section>
        <div class="section-head">
          <h2>2026 上半年歌曲收藏榜</h2>
          <span class="chevron">›</span>
        </div>
        <SongColumnList
          v-if="songs.length"
          :songs="songs"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else>还没有可展示的歌曲。</EmptyState>
      </section>

      <PlaylistSection v-if="playlists.length" title="新歌单精选" :playlists="playlists" />

      <section v-if="recentSongIds.length" class="compact-panel">
        <div class="section-head">
          <h2>最近播放</h2>
          <span class="chevron">›</span>
        </div>
        <p class="muted-line">最近播放记录已同步：{{ recentSongIds.join("、") }}</p>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { playlistApi, songApi, userApi } from "@/api";
import type { Playlist, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import FeatureScroller from "@/components/FeatureScroller.vue";
import PlaylistSection from "@/components/PlaylistSection.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { useAuthStore } from "@/stores/auth";
import { usePlayerStore } from "@/stores/player";

const auth = useAuthStore();
const player = usePlayerStore();
const router = useRouter();
const loading = ref(true);
const songs = ref<Song[]>([]);
const playlists = ref<Playlist[]>([]);
const recentSongIds = ref<number[]>([]);

onMounted(loadDiscover);

async function loadDiscover() {
  loading.value = true;
  try {
    const [songPage, playlistPage] = await Promise.all([
      songApi.list({ page: 1, size: 16 }),
      playlistApi.list({ page: 1, size: 6 })
    ]);
    songs.value = songPage.items;
    playlists.value = playlistPage.items;
    if (auth.isAuthenticated) {
      const recent = await userApi.recentPlays(1, 8);
      recentSongIds.value = recent.items.map((item) => item.songId);
    }
  } finally {
    loading.value = false;
  }
}

function playFirst() {
  playSong(songs.value[0]);
}

function playSong(song?: Song) {
  if (!song) return;
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
</script>
