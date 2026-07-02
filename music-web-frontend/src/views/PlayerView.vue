<template>
  <section class="player-page" :style="themeStyle">
    <div class="player-page-backdrop" aria-hidden="true">
      <img v-if="player.currentSong?.coverUrl" :src="resolveMediaUrl(player.currentSong.coverUrl)" alt="" />
    </div>

    <header class="player-page-head">
      <div class="player-nav-actions">
        <button class="round-icon-button" type="button" aria-label="返回" @click="goBack">
          <ArrowLeft :size="20" />
          <span>返回</span>
        </button>
        <RouterLink class="round-icon-button" to="/discover" aria-label="返回首页">
          <Home :size="20" />
          <span>首页</span>
        </RouterLink>
      </div>
    </header>

    <EmptyState v-if="!player.currentSong" class="player-empty">
      还没有正在播放的歌曲。
      <RouterLink class="link-button" to="/discover">去发现音乐</RouterLink>
    </EmptyState>

    <template v-else>
      <section class="player-stage">
        <aside class="player-album-panel">
          <div class="turntable-card">
            <div class="record-disc" :class="{ 'record-disc-playing': player.isPlaying }">
              <div class="record-grooves" aria-hidden="true" />
              <div class="record-cover">
                <img v-if="player.currentSong.coverUrl" :src="resolveMediaUrl(player.currentSong.coverUrl)" alt="" />
                <Music v-else :size="54" />
              </div>
            </div>
            <div class="tone-arm" aria-hidden="true">
              <span />
            </div>
          </div>
          <div class="player-song-copy">
            <h2>{{ player.currentSong.title }}</h2>
            <p>{{ player.currentSong.artistName || "未知歌手" }} · {{ player.currentSong.albumTitle || "未知专辑" }}</p>
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
      </section>

      <footer class="player-bottom-dock" aria-label="播放控制">
        <section v-if="queueOpen" class="play-queue-panel player-page-queue-panel" aria-label="播放列表" @wheel.stop @touchmove.stop>
          <div class="play-queue-head">
            <div>
              <strong>播放列表</strong>
              <span>{{ player.queue.length }} 首 · {{ player.playModeLabel }}</span>
            </div>
            <button type="button" aria-label="关闭播放列表" @click="queueOpen = false">
              <X :size="16" />
            </button>
          </div>

          <div class="play-mode-tabs" aria-label="播放模式">
            <button type="button" :class="{ active: player.playMode === 'order' }" @click="player.setPlayMode('order')">
              <Repeat :size="15" />
              <span>顺序</span>
            </button>
            <button type="button" :class="{ active: player.playMode === 'shuffle' }" @click="player.setPlayMode('shuffle')">
              <Shuffle :size="15" />
              <span>随机</span>
            </button>
            <button type="button" :class="{ active: player.playMode === 'repeat-one' }" @click="player.setPlayMode('repeat-one')">
              <Repeat1 :size="15" />
              <span>单曲</span>
            </button>
          </div>

          <div v-if="player.queue.length" class="play-queue-list">
            <div
              v-for="song in player.queue"
              :key="song.id"
              class="play-queue-item"
              :class="{ active: player.currentSong?.id === song.id }"
            >
              <button type="button" class="play-queue-song" @click="playQueuedSong(song)">
                <img v-if="song.coverUrl" :src="resolveMediaUrl(song.coverUrl)" alt="" />
                <Music v-else :size="16" />
                <span>
                  <strong>{{ song.title }}</strong>
                  <small>{{ song.artistName || "未知歌手" }}</small>
                </span>
              </button>
              <button type="button" class="queue-remove" :aria-label="`从播放列表移除 ${song.title}`" @click="player.removeFromQueue(song.id)">
                <Trash2 :size="15" />
              </button>
            </div>
          </div>
          <p v-else class="queue-empty">播放列表为空。</p>

          <button v-if="player.queue.length" type="button" class="queue-clear" @click="player.clearQueue()">清空播放列表</button>
        </section>

        <div class="player-progress-control">
          <span>{{ formatDuration(player.currentTime) }}</span>
          <input
            aria-label="播放进度"
            type="range"
            min="0"
            :max="rangeMax"
            step="0.1"
            :value="player.currentTime"
            @input="seekFromRange"
          />
          <span>{{ formatDuration(player.duration) }}</span>
        </div>

        <div class="player-dock-row">
          <div class="player-mini-meta">
            <img v-if="player.currentSong.coverUrl" :src="resolveMediaUrl(player.currentSong.coverUrl)" alt="" />
            <Music v-else :size="18" />
            <div>
              <strong>{{ player.currentSong.title }}</strong>
              <span>{{ player.currentSong.artistName || "未知歌手" }}</span>
            </div>
          </div>

          <div class="player-page-controls">
            <button type="button" :aria-label="player.playModeLabel" :title="player.playModeLabel" @click="player.cyclePlayMode()">
              <Shuffle v-if="player.playMode === 'shuffle'" :size="20" />
              <Repeat1 v-else-if="player.playMode === 'repeat-one'" :size="20" />
              <Repeat v-else :size="20" />
            </button>
            <button type="button" aria-label="上一首" @click="player.previous">
              <SkipBack :size="22" />
            </button>
            <button class="player-page-toggle" type="button" :aria-label="player.isPlaying ? '暂停' : '播放'" @click="togglePlay">
              <Pause v-if="player.isPlaying" :size="24" fill="currentColor" />
              <Play v-else :size="24" fill="currentColor" />
            </button>
            <button type="button" aria-label="下一首" @click="player.next">
              <SkipForward :size="22" />
            </button>
            <button type="button" aria-label="打开播放列表" :aria-expanded="queueOpen" @click="queueOpen = !queueOpen">
              <ListMusic :size="20" />
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
        </div>
      </footer>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { ArrowLeft, Home, ListMusic, Music, Pause, Play, Repeat, Repeat1, Shuffle, SkipBack, SkipForward, Trash2, Volume2, X } from "lucide-vue-next";
import EmptyState from "@/components/EmptyState.vue";
import LyricPanel from "@/components/LyricPanel.vue";
import { usePlayerStore } from "@/stores/player";
import { formatDuration, resolveMediaUrl } from "@/utils/format";
import type { Song } from "@/api/types";

const router = useRouter();
const player = usePlayerStore();
const queueOpen = ref(false);
const rangeMax = computed(() => Math.max(player.duration || player.currentSong?.durationSeconds || 1, 1));
const DEFAULT_THEME = { r: 68, g: 73, b: 84 };
const THEME_CACHE_PREFIX = "melospace-player-theme:";
const theme = ref(readCachedTheme(player.currentSong?.coverUrl) ?? DEFAULT_THEME);
const themeStyle = computed(() => ({
  "--player-theme": `rgb(${theme.value.r}, ${theme.value.g}, ${theme.value.b})`,
  "--player-theme-soft": `rgba(${theme.value.r}, ${theme.value.g}, ${theme.value.b}, 0.34)`,
  "--player-theme-muted": `rgba(${theme.value.r}, ${theme.value.g}, ${theme.value.b}, 0.16)`,
  "--player-bg-start": `rgb(${Math.max(theme.value.r - 82, 18)}, ${Math.max(theme.value.g - 72, 18)}, ${Math.max(theme.value.b - 68, 18)})`,
  "--player-bg-end": `rgb(${Math.max(theme.value.r - 118, 12)}, ${Math.max(theme.value.g - 104, 12)}, ${Math.max(theme.value.b - 98, 12)})`
}));
let themeRequestId = 0;

watch(
  () => player.currentSong?.coverUrl,
  async (coverUrl) => {
    const requestId = themeRequestId + 1;
    themeRequestId = requestId;
    const cachedTheme = readCachedTheme(coverUrl);
    theme.value = cachedTheme ?? DEFAULT_THEME;
    const extractedTheme = await extractThemeColor(coverUrl);
    if (requestId !== themeRequestId) return;
    theme.value = extractedTheme;
    writeCachedTheme(coverUrl, extractedTheme);
  },
  { immediate: true }
);

function goBack() {
  if (window.history.state?.back) {
    router.back();
  } else {
    router.push("/discover");
  }
}

function togglePlay() {
  if (!player.currentSong) return;
  if (player.isPlaying) {
    player.setPlaying(false);
    return;
  }
  void player.resumeCurrent();
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

function playQueuedSong(song: Song) {
  void player.playSong(song, player.queue);
}

function extractThemeColor(coverUrl?: string | null) {
  return new Promise<{ r: number; g: number; b: number }>((resolve) => {
    if (!coverUrl) {
      resolve(DEFAULT_THEME);
      return;
    }

    const image = new Image();
    image.crossOrigin = "anonymous";
    image.onload = () => {
      const canvas = document.createElement("canvas");
      const size = 48;
      canvas.width = size;
      canvas.height = size;
      const context = canvas.getContext("2d", { willReadFrequently: true });
      if (!context) {
        resolve(DEFAULT_THEME);
        return;
      }

      context.drawImage(image, 0, 0, size, size);
      const pixels = context.getImageData(0, 0, size, size).data;
      let red = 0;
      let green = 0;
      let blue = 0;
      let weightTotal = 0;

      for (let index = 0; index < pixels.length; index += 16) {
        const r = pixels[index];
        const g = pixels[index + 1];
        const b = pixels[index + 2];
        const alpha = pixels[index + 3];
        const brightness = (r + g + b) / 3;
        const saturation = Math.max(r, g, b) - Math.min(r, g, b);
        if (alpha < 180 || brightness < 24 || brightness > 236) continue;

        const weight = saturation + 32;
        red += r * weight;
        green += g * weight;
        blue += b * weight;
        weightTotal += weight;
      }

      if (!weightTotal) {
        resolve(DEFAULT_THEME);
        return;
      }
      resolve({
        r: Math.round(red / weightTotal),
        g: Math.round(green / weightTotal),
        b: Math.round(blue / weightTotal)
      });
    };
    image.onerror = () => resolve(DEFAULT_THEME);
    image.src = resolveMediaUrl(coverUrl);
  });
}

function readCachedTheme(coverUrl?: string | null) {
  if (!coverUrl) return null;
  const raw = localStorage.getItem(`${THEME_CACHE_PREFIX}${coverUrl}`);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as { r?: number; g?: number; b?: number };
    if ([parsed.r, parsed.g, parsed.b].every((value) => typeof value === "number")) {
      return { r: parsed.r as number, g: parsed.g as number, b: parsed.b as number };
    }
  } catch {
    localStorage.removeItem(`${THEME_CACHE_PREFIX}${coverUrl}`);
  }
  return null;
}

function writeCachedTheme(coverUrl: string | null | undefined, nextTheme: { r: number; g: number; b: number }) {
  if (!coverUrl) return;
  localStorage.setItem(`${THEME_CACHE_PREFIX}${coverUrl}`, JSON.stringify(nextTheme));
}
</script>
