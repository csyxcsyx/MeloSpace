<template>
  <section class="player-page">
    <header class="player-page-head">
      <button class="round-icon-button" type="button" aria-label="返回" @click="goBack">
        <ArrowLeft :size="22" />
      </button>
      <div>
        <p class="feature-label">正在播放</p>
        <h1>{{ player.currentSong?.title || "Orange Music" }}</h1>
      </div>
    </header>

    <EmptyState v-if="!player.currentSong" class="player-empty">
      还没有正在播放的歌曲。
      <RouterLink class="link-button" to="/discover">去发现音乐</RouterLink>
    </EmptyState>

    <template v-else>
      <section class="player-stage">
        <aside class="player-album-panel">
          <div class="player-cover-large">
            <img v-if="player.currentSong.coverUrl" :src="resolveMediaUrl(player.currentSong.coverUrl)" alt="" />
            <Music v-else :size="56" />
          </div>
          <div class="player-song-copy">
            <h2>{{ player.currentSong.title }}</h2>
            <p>{{ player.currentSong.artistName || "未知歌手" }} · {{ player.currentSong.albumTitle || "未知专辑" }}</p>
          </div>

          <div class="player-progress-control">
            <input
              aria-label="播放进度"
              type="range"
              min="0"
              :max="rangeMax"
              step="0.1"
              :value="player.currentTime"
              @input="seekFromRange"
            />
            <div class="player-time-row">
              <span>{{ formatDuration(player.currentTime) }}</span>
              <span>{{ formatDuration(player.duration) }}</span>
            </div>
          </div>

          <div class="player-page-controls" aria-label="播放控制">
            <button type="button" aria-label="上一首" @click="player.previous">
              <SkipBack :size="24" />
            </button>
            <button class="player-page-toggle" type="button" :aria-label="player.isPlaying ? '暂停' : '播放'" @click="togglePlay">
              <Pause v-if="player.isPlaying" :size="26" fill="currentColor" />
              <Play v-else :size="26" fill="currentColor" />
            </button>
            <button type="button" aria-label="下一首" @click="player.next">
              <SkipForward :size="24" />
            </button>
          </div>

          <div class="player-volume-row">
            <Volume2 :size="18" />
            <input
              aria-label="音量"
              type="range"
              min="0"
              max="1"
              step="0.01"
              :value="player.volume"
              @input="setVolume"
            />
          </div>
        </aside>

        <section class="player-lyrics-panel">
          <LyricPanel
            :song="player.currentSong"
            :current-time="player.currentTime"
            :is-current-song="true"
            fullscreen
            @seek="seekLyric"
          />
        </section>

        <aside class="player-queue-panel">
          <div class="section-head">
            <ListMusic :size="18" />
            <h2>播放队列</h2>
          </div>
          <div class="player-queue-list">
            <button
              v-for="song in player.queue"
              :key="song.id"
              type="button"
              :class="{ active: song.id === player.currentSong.id }"
              @click="player.playSong(song, player.queue)"
            >
              <span>{{ song.title }}</span>
              <small>{{ song.artistName || "未知歌手" }}</small>
            </button>
          </div>
        </aside>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { ArrowLeft, ListMusic, Music, Pause, Play, SkipBack, SkipForward, Volume2 } from "lucide-vue-next";
import EmptyState from "@/components/EmptyState.vue";
import LyricPanel from "@/components/LyricPanel.vue";
import { usePlayerStore } from "@/stores/player";
import { formatDuration, resolveMediaUrl } from "@/utils/format";

const router = useRouter();
const player = usePlayerStore();
const rangeMax = computed(() => Math.max(player.duration || player.currentSong?.durationSeconds || 1, 1));

function goBack() {
  if (window.history.state?.back) {
    router.back();
  } else {
    router.push("/discover");
  }
}

function togglePlay() {
  if (!player.currentSong) return;
  player.setPlaying(!player.isPlaying);
}

function seekLyric(time: number) {
  player.seekTo(time, true);
}

function seekFromRange(event: Event) {
  const input = event.target as HTMLInputElement;
  player.seekTo(Number(input.value), true);
}

function setVolume(event: Event) {
  const input = event.target as HTMLInputElement;
  player.setVolume(Number(input.value));
}
</script>
