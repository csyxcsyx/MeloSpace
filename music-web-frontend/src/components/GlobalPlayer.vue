<template>
  <footer class="player" :class="{ 'player-hidden': hidden }" :aria-hidden="hidden" aria-label="全局播放器">
    <audio
      ref="audioRef"
      :src="audioSrc"
      :volume="player.volume"
      preload="metadata"
      @loadedmetadata="onLoadedMetadata"
      @timeupdate="onTimeUpdate"
      @ended="player.next"
      @waiting="player.setLoading(true)"
      @canplay="player.setLoading(false)"
      @error="onAudioError"
    />

    <div class="player-controls">
      <button aria-label="上一首" type="button" @click="player.previous">
        <SkipBack :size="18" />
      </button>
      <button class="play-toggle" :aria-label="player.isPlaying ? '暂停' : '播放'" type="button" @click="togglePlay">
        <Pause v-if="player.isPlaying" :size="18" fill="currentColor" />
        <Play v-else :size="18" fill="currentColor" />
      </button>
      <button aria-label="下一首" type="button" @click="player.next">
        <SkipForward :size="18" />
      </button>
    </div>

    <div class="now-playing" :class="{ empty: !player.currentSong }">
      <div class="now-cover">
        <img v-if="player.currentSong?.coverUrl" :src="resolveMediaUrl(player.currentSong.coverUrl)" alt="" />
        <Music v-else :size="18" />
      </div>
      <div class="now-text">
        <div class="now-title">{{ player.currentSong?.title || "选择一首歌开始播放" }}</div>
        <div class="now-meta">
          {{ player.errorMessage || player.currentSong?.artistName || "MeloSpace" }}
        </div>
        <div class="mini-progress" aria-label="播放进度" role="slider" tabindex="0" @click="seek">
          <span :style="{ width: `${player.progressPercent}%` }" />
        </div>
      </div>
    </div>

    <div class="player-tools">
      <button
        class="player-tool-button"
        type="button"
        aria-label="打开全屏歌词"
        :disabled="!player.currentSong"
        @click="openPlayer"
      >
        <Maximize2 :size="16" />
      </button>
      <span>{{ formatDuration(player.currentTime) }}</span>
      <Volume2 :size="16" />
      <input
        class="volume-range"
        aria-label="音量"
        type="range"
        min="0"
        max="1"
        step="0.01"
        :value="player.volume"
        @input="setVolume"
      />
      <span>{{ formatDuration(player.duration) }}</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Maximize2, Music, Pause, Play, SkipBack, SkipForward, Volume2 } from "lucide-vue-next";
import { usePlayerStore } from "@/stores/player";
import type { Song } from "@/api/types";
import { PLAYER_PLAY_REQUEST_EVENT } from "@/stores/player";
import { formatDuration, resolveMediaUrl } from "@/utils/format";

withDefaults(defineProps<{ hidden?: boolean }>(), {
  hidden: false
});

const router = useRouter();
const player = usePlayerStore();
const audioRef = ref<HTMLAudioElement | null>(null);
const audioSrc = computed(() => resolveMediaUrl(player.currentSong?.audioUrl));

interface PlayRequestDetail {
  song: Song;
  time?: number;
  shouldPlay?: boolean;
}

watch(
  () => player.currentSong?.id,
  async () => {
    await nextTick();
    const audio = audioRef.value;
    if (!audio) return;
    if (audio.getAttribute("src") !== audioSrc.value) {
      audio.load();
    }
    if (player.isPlaying && audio.paused) {
      playAudio();
    }
  },
  { flush: "post" }
);

watch(
  () => player.isPlaying,
  (isPlaying) => {
    if (isPlaying) {
      playAudio();
    } else {
      audioRef.value?.pause();
    }
  },
  { flush: "post" }
);

watch(
  () => player.volume,
  (volume) => {
    if (audioRef.value) audioRef.value.volume = volume;
  },
  { immediate: true }
);

watch(
  () => player.seekRequestId,
  async () => {
    await nextTick();
    const audio = audioRef.value;
    if (!audio || !audioSrc.value) return;
    const nextTime = Math.min(player.seekTarget, Number.isFinite(audio.duration) ? audio.duration : player.seekTarget);
    audio.currentTime = nextTime;
    player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : player.duration);
    if (player.seekShouldPlay) {
      player.setPlaying(true);
      playAudio();
    }
  }
);

onMounted(() => {
  window.addEventListener(PLAYER_PLAY_REQUEST_EVENT, handlePlayRequest);
});

onBeforeUnmount(() => {
  window.removeEventListener(PLAYER_PLAY_REQUEST_EVENT, handlePlayRequest);
});

function playAudio() {
  const audio = audioRef.value;
  if (!audio || !audioSrc.value) {
    player.setPlaying(false);
    return;
  }
  audio.play().catch(() => {
    player.setError("浏览器阻止了自动播放，请再次点击播放");
  });
}

function togglePlay() {
  if (!player.currentSong) return;
  if (player.isPlaying) {
    player.setPlaying(false);
    return;
  }
  player.setPlaying(true);
  playAudio();
}

function onLoadedMetadata() {
  const audio = audioRef.value;
  if (!audio) return;
  player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : 0);
}

function onTimeUpdate() {
  const audio = audioRef.value;
  if (!audio) return;
  player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : 0);
}

function onAudioError() {
  player.setError("音频加载失败，请检查媒体文件或代理配置");
}

function seek(event: MouseEvent) {
  const audio = audioRef.value;
  const target = event.currentTarget as HTMLElement;
  if (!audio || !player.duration) return;
  const rect = target.getBoundingClientRect();
  const percent = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
  audio.currentTime = player.duration * percent;
  player.setTime(audio.currentTime, player.duration);
}

function setVolume(event: Event) {
  const input = event.target as HTMLInputElement;
  player.setVolume(Number(input.value));
}

function handlePlayRequest(event: Event) {
  const audio = audioRef.value;
  const detail = (event as CustomEvent<PlayRequestDetail>).detail;
  const song = detail?.song;
  if (!audio || !song) return;

  const nextSrc = resolveMediaUrl(song.audioUrl);
  if (!nextSrc) return;
  if (audio.getAttribute("src") !== nextSrc) {
    audio.src = nextSrc;
    audio.setAttribute("src", nextSrc);
    audio.load();
  }
  if (typeof detail.time === "number" && Number.isFinite(detail.time)) {
    audio.currentTime = Math.max(0, detail.time);
    player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : player.duration);
  }
  if (detail.shouldPlay !== false) {
    audio.play().catch(() => {
      player.setError("浏览器阻止了自动播放，请再次点击播放");
    });
  }
}

function openPlayer() {
  if (!player.currentSong) return;
  router.push("/player");
}
</script>
