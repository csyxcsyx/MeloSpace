<template>
  <footer class="player" :class="{ 'player-hidden': hidden }" :aria-hidden="hidden" aria-label="全局播放器">
    <audio
      ref="audioRef"
      :volume="player.volume"
      preload="metadata"
      @loadedmetadata="onLoadedMetadata"
      @timeupdate="onTimeUpdate"
      @ended="handleEnded"
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
      <button
        class="now-cover now-cover-actionable"
        type="button"
        :disabled="!player.currentSong"
        aria-label="进入全屏歌词"
        title="进入全屏歌词"
        @click="openPlayer"
      >
        <img v-if="player.currentSong?.coverUrl" :src="resolveMediaUrl(player.currentSong.coverUrl)" alt="" />
        <Music v-else :size="18" />
        <span class="now-cover-lyric" aria-hidden="true">
          <Maximize2 :size="15" />
          <span>歌词</span>
        </span>
      </button>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
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
let activePlayRequest: Promise<boolean> | null = null;
let playRequestToken = 0;

interface PlayRequestDetail {
  song: Song;
  time?: number;
  shouldPlay?: boolean;
  respond?: (played: boolean) => void;
}

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
  () => {
    const audio = audioRef.value;
    if (!audio || !audioSrc.value) return;
    prepareCurrentAudioSource();
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
    return Promise.resolve(false);
  }
  prepareCurrentAudioSource();
  if (activePlayRequest) return activePlayRequest;
  if (!audio.paused) {
    player.setPlaying(true);
    return Promise.resolve(true);
  }

  player.setLoading(true);
  const token = ++playRequestToken;
  const request = audio.play()
    .then(() => {
      player.setPlaying(true);
      player.setLoading(false);
      return true;
    })
    .catch(async (error: unknown) => {
      if (isAbortError(error) && token === playRequestToken) {
        const ready = await waitForPlayable(audio, token);
        if (ready) {
          return retryPlay(audio, token);
        }
      }
      if (token === playRequestToken) {
        player.setError(playbackErrorMessage(error));
      }
      return false;
    })
    .finally(() => {
      if (activePlayRequest === request) {
        activePlayRequest = null;
      }
    });

  activePlayRequest = request;
  return request;
}

function togglePlay() {
  if (!player.currentSong) return;
  if (player.isPlaying) {
    player.setPlaying(false);
    return;
  }
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

async function handleEnded() {
  const audio = audioRef.value;
  const nextSong = player.getNextSong(false);
  if (!audio || !nextSong) {
    player.setTime(player.duration, player.duration);
    player.setPlaying(false);
    player.setLoading(false);
    return;
  }

  const nextSrc = resolveMediaUrl(nextSong.audioUrl);
  if (!nextSrc) {
    player.setError("下一首音频地址无效");
    return;
  }

  player.replaceCurrentSong(nextSong, player.queue);

  setAudioSource(audio, nextSrc);
  audio.currentTime = 0;
  player.setLoading(true);

  audio.play()
    .then(() => {
      player.setPlaying(true);
      player.setLoading(false);
    })
    .catch(() => {
      player.setError("浏览器阻止了自动播放，请再次点击播放");
    });
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
  setAudioSource(audio, nextSrc);
  if (typeof detail.time === "number" && Number.isFinite(detail.time)) {
    audio.currentTime = Math.max(0, detail.time);
    player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : player.duration);
  }
  if (detail.shouldPlay !== false) {
    void playAudio().then((played) => detail.respond?.(played));
  } else {
    detail.respond?.(true);
  }
}

function playbackErrorMessage(error: unknown) {
  if (error instanceof DOMException) {
    if (error.name === "NotAllowedError") {
      return "浏览器阻止了自动播放，请点击播放";
    }
    if (error.name === "AbortError") {
      return "音频播放失败，请再次点击播放";
    }
  }
  return "音频播放失败，请再次点击播放";
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === "AbortError";
}

function waitForPlayable(audio: HTMLAudioElement, token: number) {
  if (audio.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA) {
    return Promise.resolve(token === playRequestToken);
  }

  return new Promise<boolean>((resolve) => {
    const cleanup = () => {
      audio.removeEventListener("canplay", handleCanPlay);
      audio.removeEventListener("loadeddata", handleCanPlay);
      audio.removeEventListener("error", handleError);
      window.clearTimeout(timeoutId);
    };
    const handleCanPlay = () => {
      cleanup();
      resolve(token === playRequestToken);
    };
    const handleError = () => {
      cleanup();
      resolve(false);
    };
    const timeoutId = window.setTimeout(() => {
      cleanup();
      resolve(token === playRequestToken && audio.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA);
    }, 1200);

    audio.addEventListener("canplay", handleCanPlay, { once: true });
    audio.addEventListener("loadeddata", handleCanPlay, { once: true });
    audio.addEventListener("error", handleError, { once: true });
  });
}

async function retryPlay(audio: HTMLAudioElement, token: number) {
  try {
    await audio.play();
    if (token === playRequestToken) {
      player.setPlaying(true);
      player.setLoading(false);
    }
    return true;
  } catch (error) {
    if (token === playRequestToken) {
      player.setError(playbackErrorMessage(error));
    }
    return false;
  }
}

function prepareCurrentAudioSource() {
  const audio = audioRef.value;
  if (!audio || !audioSrc.value) return;
  setAudioSource(audio, audioSrc.value);
}

function setAudioSource(audio: HTMLAudioElement, src: string) {
  if (audio.currentSrc === src || audio.getAttribute("src") === src) return;
  playRequestToken += 1;
  activePlayRequest = null;
  audio.pause();
  audio.src = src;
  audio.setAttribute("src", src);
}

function openPlayer() {
  if (!player.currentSong) return;
  router.push("/player");
}
</script>
