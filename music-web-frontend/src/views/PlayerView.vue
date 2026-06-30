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
import { ArrowLeft, Home, Music, Pause, Play, SkipBack, SkipForward, Volume2 } from "lucide-vue-next";
import EmptyState from "@/components/EmptyState.vue";
import LyricPanel from "@/components/LyricPanel.vue";
import { usePlayerStore } from "@/stores/player";
import { formatDuration, resolveMediaUrl } from "@/utils/format";

const router = useRouter();
const player = usePlayerStore();
const rangeMax = computed(() => Math.max(player.duration || player.currentSong?.durationSeconds || 1, 1));
const theme = ref({ r: 250, g: 117, b: 42 });
const themeStyle = computed(() => ({
  "--player-theme": `rgb(${theme.value.r}, ${theme.value.g}, ${theme.value.b})`,
  "--player-theme-soft": `rgba(${theme.value.r}, ${theme.value.g}, ${theme.value.b}, 0.34)`,
  "--player-theme-muted": `rgba(${theme.value.r}, ${theme.value.g}, ${theme.value.b}, 0.16)`,
  "--player-bg-start": `rgb(${Math.max(theme.value.r - 82, 18)}, ${Math.max(theme.value.g - 72, 18)}, ${Math.max(theme.value.b - 68, 18)})`,
  "--player-bg-end": `rgb(${Math.max(theme.value.r - 118, 12)}, ${Math.max(theme.value.g - 104, 12)}, ${Math.max(theme.value.b - 98, 12)})`
}));

watch(
  () => player.currentSong?.coverUrl,
  async (coverUrl) => {
    theme.value = await extractThemeColor(coverUrl);
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

function extractThemeColor(coverUrl?: string | null) {
  return new Promise<{ r: number; g: number; b: number }>((resolve) => {
    if (!coverUrl) {
      resolve({ r: 250, g: 117, b: 42 });
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
        resolve({ r: 250, g: 117, b: 42 });
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
        resolve({ r: 250, g: 117, b: 42 });
        return;
      }
      resolve({
        r: Math.round(red / weightTotal),
        g: Math.round(green / weightTotal),
        b: Math.round(blue / weightTotal)
      });
    };
    image.onerror = () => resolve({ r: 250, g: 117, b: 42 });
    image.src = resolveMediaUrl(coverUrl);
  });
}
</script>
