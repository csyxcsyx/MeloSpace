<template>
  <footer class="player" :class="{ 'player-hidden': hidden }" :aria-hidden="hidden" aria-label="全局播放器" data-glass="regular">
    <audio
      ref="audioRef"
      :volume="player.volume"
      preload="metadata"
      @loadedmetadata="onLoadedMetadata"
      @durationchange="syncMediaPosition"
      @timeupdate="onTimeUpdate"
      @play="onAudioPlay"
      @pause="onAudioPause"
      @ended="handleEnded"
      @waiting="player.setLoading(true)"
      @canplay="player.setLoading(false)"
      @error="onAudioError"
    />

    <section v-if="queueOpen" class="play-queue-panel" aria-label="播放列表" @wheel.stop @touchmove.stop>
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

    <div class="player-controls">
      <button aria-label="上一首" type="button" @click="player.previous()">
        <SkipBack :size="18" />
      </button>
      <button class="play-toggle" :aria-label="player.isPlaying ? '暂停' : '播放'" type="button" @click="togglePlay">
        <Pause v-if="player.isPlaying" :size="18" fill="currentColor" />
        <Play v-else :size="18" fill="currentColor" />
      </button>
      <button aria-label="下一首" type="button" @click="player.next()">
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
      <button class="player-tool-button" type="button" :aria-label="player.playModeLabel" :title="player.playModeLabel" @click="player.cyclePlayMode()">
        <Shuffle v-if="player.playMode === 'shuffle'" :size="16" />
        <Repeat1 v-else-if="player.playMode === 'repeat-one'" :size="16" />
        <Repeat v-else :size="16" />
      </button>
      <button
        class="player-tool-button"
        type="button"
        aria-label="打开播放列表"
        title="播放列表"
        :aria-expanded="queueOpen"
        @click="queueOpen = !queueOpen"
      >
        <ListMusic :size="16" />
      </button>
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
import { ListMusic, Maximize2, Music, Pause, Play, Repeat, Repeat1, Shuffle, SkipBack, SkipForward, Trash2, Volume2, X } from "lucide-vue-next";
import { usePlayerStore } from "@/stores/player";
import { useAuthStore } from "@/stores/auth";
import { useUiStore } from "@/stores/ui";
import type { Song } from "@/api/types";
import { PLAYER_PLAY_REQUEST_EVENT } from "@/stores/player";
import { formatDuration, resolveMediaUrl } from "@/utils/format";

withDefaults(defineProps<{ hidden?: boolean }>(), {
  hidden: false
});

const router = useRouter();
const player = usePlayerStore();
const auth = useAuthStore();
const ui = useUiStore();
const audioRef = ref<HTMLAudioElement | null>(null);
const queueOpen = ref(false);
const audioSrc = computed(() => resolveMediaUrl(player.currentSong?.audioUrl));
let activePlayRequest: Promise<boolean> | null = null;
let playRequestToken = 0;
const PROGRESS_SYNC_INTERVAL_MS = 100;
let progressTimer: number | null = null;

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
      void playAudio().then((played) => {
        if (played) startProgressLoop();
      });
    } else {
      audioRef.value?.pause();
      stopProgressLoop();
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
  () => player.currentSong,
  (song) => {
    updateMediaMetadata(auth.isAuthenticated ? song : null);
  },
  { immediate: true }
);

watch(
  () => auth.isAuthenticated,
  (isAuthenticated) => {
    if (isAuthenticated) {
      updateMediaMetadata(player.currentSong);
      return;
    }
    audioRef.value?.pause();
    player.setPlaying(false);
    player.setLoading(false);
    clearMediaSession();
  }
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
    syncMediaPosition();
    if (player.seekShouldPlay) {
      player.setPlaying(true);
      playAudio();
    }
  }
);

onMounted(() => {
  window.addEventListener(PLAYER_PLAY_REQUEST_EVENT, handlePlayRequest);
  registerMediaSessionHandlers();
  updateMediaMetadata(auth.isAuthenticated ? player.currentSong : null);
});

onBeforeUnmount(() => {
  window.removeEventListener(PLAYER_PLAY_REQUEST_EVENT, handlePlayRequest);
  stopProgressLoop();
  unregisterMediaSessionHandlers();
  clearMediaSession();
});

function playAudio() {
  if (!ensureAuthenticatedPlayback()) {
    return Promise.resolve(false);
  }
  const audio = audioRef.value;
  if (!audio || !audioSrc.value) {
    player.setPlaying(false);
    return Promise.resolve(false);
  }
  prepareCurrentAudioSource();
  if (activePlayRequest) return activePlayRequest;
  if (!audio.paused) {
    player.setPlaying(true);
    setMediaPlaybackState("playing");
    syncMediaPosition();
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
  if (!ensureAuthenticatedPlayback()) return;
  playAudio();
}

function onLoadedMetadata() {
  const audio = audioRef.value;
  if (!audio) return;
  player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : 0);
  syncMediaPosition();
}

function onTimeUpdate() {
  const audio = audioRef.value;
  if (!audio) return;
  player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : 0);
  syncMediaPosition();
}

function onAudioPlay() {
  player.setPlaying(true);
  setMediaPlaybackState("playing");
  syncMediaPosition();
  startProgressLoop();
}

function onAudioPause() {
  stopProgressLoop();
  if (!audioRef.value?.ended) {
    setMediaPlaybackState(player.currentSong ? "paused" : "none");
    syncMediaPosition();
  }
}

function startProgressLoop() {
  if (progressTimer !== null) return;
  syncProgressTime();
  progressTimer = window.setInterval(syncProgressTime, PROGRESS_SYNC_INTERVAL_MS);
}

function stopProgressLoop() {
  if (progressTimer === null) return;
  window.clearInterval(progressTimer);
  progressTimer = null;
}

function syncProgressTime() {
  const audio = audioRef.value;
  if (!audio) {
    stopProgressLoop();
    return;
  }

  player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : 0);
  if (!player.isPlaying || audio.paused || audio.ended) {
    stopProgressLoop();
  }
}

function onAudioError() {
  stopProgressLoop();
  setMediaPlaybackState("paused");
  player.setError("音频加载失败，请检查媒体文件或代理配置");
}

async function handleEnded() {
  if (!ensureAuthenticatedPlayback()) return;
  const audio = audioRef.value;
  const nextSong = player.getNextSong(false);
  if (!audio || !nextSong) {
    player.setTime(player.duration, player.duration);
    player.setPlaying(false);
    player.setLoading(false);
    setMediaPlaybackState("paused");
    syncMediaPosition();
    return;
  }

  const nextSrc = resolveMediaUrl(nextSong.audioUrl);
  if (!nextSrc) {
    player.setError("下一首音频地址无效");
    return;
  }

  player.replaceCurrentSong(nextSong, player.queue, true);

  setAudioSource(audio, nextSrc);
  audio.currentTime = 0;
  player.setLoading(true);

  audio.play()
    .then(() => {
      player.setPlaying(true);
      player.setLoading(false);
    })
    .catch((error: unknown) => {
      player.setError(playbackErrorMessage(error));
    });
}

function ensureAuthenticatedPlayback() {
  if (auth.isAuthenticated) return true;
  const message = "请先登录后播放音乐";
  audioRef.value?.pause();
  player.setError(message);
  ui.toast(message);
  return false;
}

function seek(event: MouseEvent) {
  const audio = audioRef.value;
  const target = event.currentTarget as HTMLElement;
  if (!audio || !player.duration) return;
  const rect = target.getBoundingClientRect();
  const percent = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
  audio.currentTime = player.duration * percent;
  player.setTime(audio.currentTime, player.duration);
  syncMediaPosition();
}

function setVolume(event: Event) {
  const input = event.target as HTMLInputElement;
  player.setVolume(Number(input.value));
}

function playQueuedSong(song: Song) {
  void player.playSong(song, player.queue);
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

function hasMediaSession() {
  return typeof navigator !== "undefined" && "mediaSession" in navigator;
}

function updateMediaMetadata(song: Song | null) {
  if (!song) {
    clearMediaSession();
    return;
  }

  const artist = song.artistName || "未知歌手";
  const album = song.albumTitle || "MeloSpace";
  document.title = `${song.title} — ${artist} | MeloSpace`;
  if (!hasMediaSession() || typeof MediaMetadata === "undefined") return;

  try {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: song.title,
      artist,
      album,
      artwork: [{ src: absoluteArtworkUrl(song.coverUrl) }]
    });
  } catch {
    // Older Safari versions can expose Media Session without full metadata support.
  }
}

function clearMediaSession() {
  if (hasMediaSession()) {
    try {
      navigator.mediaSession.metadata = null;
      navigator.mediaSession.playbackState = "none";
    } catch {
      // Ignore partial Media Session implementations.
    }
  }
  document.title = "MeloSpace";
}

function absoluteArtworkUrl(coverUrl: string | null) {
  const artwork = resolveMediaUrl(coverUrl) || "/apple-touch-icon.png";
  try {
    return new URL(artwork, window.location.origin).href;
  } catch {
    return `${window.location.origin}/apple-touch-icon.png`;
  }
}

function registerMediaSessionHandlers() {
  if (!hasMediaSession()) return;
  setMediaActionHandler("play", () => {
    void playAudio();
  });
  setMediaActionHandler("pause", () => {
    player.setPlaying(false);
  });
  setMediaActionHandler("previoustrack", () => {
    void player.previous();
  });
  setMediaActionHandler("nexttrack", () => {
    void player.next();
  });
  setMediaActionHandler("seekto", (details) => {
    if (typeof details.seekTime !== "number") return;
    seekFromMediaSession(details.seekTime, details.fastSeek === true);
  });
}

function unregisterMediaSessionHandlers() {
  if (!hasMediaSession()) return;
  const actions: MediaSessionAction[] = [
    "play",
    "pause",
    "previoustrack",
    "nexttrack",
    "seekto"
  ];
  actions.forEach((action) => setMediaActionHandler(action, null));
}

function setMediaActionHandler(action: MediaSessionAction, handler: MediaSessionActionHandler | null) {
  try {
    navigator.mediaSession.setActionHandler(action, handler);
  } catch {
    // Safari support differs by release and by action.
  }
}

function seekFromMediaSession(requestedTime: number, fastSeek = false) {
  const audio = audioRef.value;
  if (!audio || !ensureAuthenticatedPlayback()) return;
  const duration = Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : player.duration;
  const nextTime = Math.max(0, duration > 0 ? Math.min(requestedTime, duration) : requestedTime);
  if (fastSeek && typeof audio.fastSeek === "function") {
    audio.fastSeek(nextTime);
  } else {
    audio.currentTime = nextTime;
  }
  player.setTime(nextTime, duration);
  syncMediaPosition();
}

function setMediaPlaybackState(state: MediaSessionPlaybackState) {
  if (!hasMediaSession()) return;
  try {
    navigator.mediaSession.playbackState = state;
  } catch {
    // Ignore browsers that expose a read-only or incomplete implementation.
  }
}

function syncMediaPosition() {
  if (!hasMediaSession() || typeof navigator.mediaSession.setPositionState !== "function") return;
  const audio = audioRef.value;
  if (!audio || !Number.isFinite(audio.duration) || audio.duration <= 0) return;
  const duration = audio.duration;
  const position = Math.min(duration, Math.max(0, Number.isFinite(audio.currentTime) ? audio.currentTime : 0));
  try {
    navigator.mediaSession.setPositionState({
      duration,
      playbackRate: Number.isFinite(audio.playbackRate) && audio.playbackRate > 0 ? audio.playbackRate : 1,
      position
    });
  } catch {
    // Metadata can change while Safari is applying a previous position update.
  }
}

function openPlayer() {
  if (!player.currentSong) return;
  router.push("/player");
}
</script>
