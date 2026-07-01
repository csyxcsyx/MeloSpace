import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";
import type { Song } from "@/api/types";
import { songApi } from "@/api";
import { useAuthStore } from "@/stores/auth";
import { useUiStore } from "@/stores/ui";

export const PLAYER_PLAY_REQUEST_EVENT = "melospace-player-play-request";
export type PlayMode = "order" | "shuffle" | "repeat-one";

const PLAYER_SONG_KEY = "melospace-player-song";
const PLAYER_QUEUE_KEY = "melospace-player-queue";
const PLAYER_VOLUME_KEY = "melospace-player-volume";
const PLAYER_MODE_KEY = "melospace-player-mode";
const LEGACY_PLAYER_SONG_KEY = "music-web-player-song";
const LEGACY_PLAYER_QUEUE_KEY = "music-web-player-queue";
const LEGACY_PLAYER_VOLUME_KEY = "music-web-player-volume";

function readStoredJson<T>(key: string) {
  const raw = localStorage.getItem(key);
  if (!raw) return { found: false, value: null as T | null };
  try {
    return { found: true, value: JSON.parse(raw) as T };
  } catch {
    localStorage.removeItem(key);
    return { found: false, value: null as T | null };
  }
}

function readJson<T>(key: string, legacyKey: string, fallback: T): T {
  const stored = readStoredJson<T>(key);
  if (stored.found) return stored.value as T;

  const legacy = readStoredJson<T>(legacyKey);
  if (!legacy.found) return fallback;
  localStorage.setItem(key, JSON.stringify(legacy.value));
  localStorage.removeItem(legacyKey);
  return legacy.value as T;
}

function readVolume() {
  const current = localStorage.getItem(PLAYER_VOLUME_KEY);
  const legacy = localStorage.getItem(LEGACY_PLAYER_VOLUME_KEY);
  if (!current && legacy) {
    localStorage.setItem(PLAYER_VOLUME_KEY, legacy);
    localStorage.removeItem(LEGACY_PLAYER_VOLUME_KEY);
  }
  const volume = Number(current ?? legacy ?? "0.8");
  return Number.isFinite(volume) ? volume : 0.8;
}

function readPlayMode(): PlayMode {
  const mode = localStorage.getItem(PLAYER_MODE_KEY);
  return mode === "shuffle" || mode === "repeat-one" || mode === "order" ? mode : "order";
}

export const usePlayerStore = defineStore("player", () => {
  const currentSong = ref<Song | null>(readJson<Song | null>(PLAYER_SONG_KEY, LEGACY_PLAYER_SONG_KEY, null));
  const queue = ref<Song[]>(readJson<Song[]>(PLAYER_QUEUE_KEY, LEGACY_PLAYER_QUEUE_KEY, []));
  const playMode = ref<PlayMode>(readPlayMode());
  const isPlaying = ref(false);
  const isLoading = ref(false);
  const currentTime = ref(0);
  const duration = ref(0);
  const volume = ref(readVolume());
  const errorMessage = ref("");
  const recordedSongIds = ref<Set<number>>(new Set());
  const seekTarget = ref(0);
  const seekRequestId = ref(0);
  const seekShouldPlay = ref(true);

  const progressPercent = computed(() => {
    if (!duration.value) return 0;
    return Math.min(100, Math.max(0, (currentTime.value / duration.value) * 100));
  });

  const playModeLabel = computed(() => {
    if (playMode.value === "shuffle") return "随机播放";
    if (playMode.value === "repeat-one") return "单曲循环";
    return "顺序播放";
  });

  function playSong(song: Song, songs: Song[] = []) {
    if (!ensurePlaybackAllowed()) return Promise.resolve(false);
    currentSong.value = song;
    queue.value = songs.length ? songs : [song];
    isPlaying.value = false;
    currentTime.value = 0;
    duration.value = 0;
    errorMessage.value = "";
    return dispatchPlayRequest(song, 0, true);
  }

  function replaceCurrentSong(song: Song, songs: Song[] = queue.value, shouldKeepPlaying = false) {
    currentSong.value = song;
    queue.value = songs.length ? songs : [song];
    isPlaying.value = shouldKeepPlaying;
    currentTime.value = 0;
    duration.value = 0;
    errorMessage.value = "";
  }

  function setPlaying(value: boolean) {
    isPlaying.value = value;
    if (value) {
      errorMessage.value = "";
    }
  }

  function setLoading(value: boolean) {
    isLoading.value = value;
  }

  function setTime(nextCurrentTime: number, nextDuration = duration.value) {
    currentTime.value = nextCurrentTime;
    duration.value = nextDuration;
    maybeRecordPlay();
  }

  function setVolume(nextVolume: number) {
    volume.value = Math.min(1, Math.max(0, nextVolume));
  }

  function setPlayMode(mode: PlayMode) {
    playMode.value = mode;
  }

  function cyclePlayMode() {
    if (playMode.value === "order") {
      setPlayMode("shuffle");
    } else if (playMode.value === "shuffle") {
      setPlayMode("repeat-one");
    } else {
      setPlayMode("order");
    }
  }

  function setError(message: string) {
    errorMessage.value = message;
    isPlaying.value = false;
    isLoading.value = false;
  }

  function seekTo(time: number, shouldPlay = true) {
    if (shouldPlay && !ensurePlaybackAllowed()) return Promise.resolve(false);
    seekTarget.value = Math.max(0, time);
    seekShouldPlay.value = shouldPlay;
    seekRequestId.value += 1;
    return dispatchPlayRequest(currentSong.value, seekTarget.value, shouldPlay);
  }

  function resumeCurrent() {
    if (!currentSong.value) return Promise.resolve(false);
    if (!ensurePlaybackAllowed()) return Promise.resolve(false);
    errorMessage.value = "";
    return dispatchPlayRequest(currentSong.value, currentTime.value, true);
  }

  function getNextSong(wrap = true) {
    if (!currentSong.value || queue.value.length === 0) return null;
    if (playMode.value === "repeat-one") return currentSong.value;
    if (playMode.value === "shuffle") {
      const candidates = queue.value.filter((song) => song.id !== currentSong.value?.id);
      if (candidates.length) {
        return candidates[Math.floor(Math.random() * candidates.length)];
      }
      return wrap ? currentSong.value : null;
    }
    const index = queue.value.findIndex((song) => song.id === currentSong.value?.id);
    if (index === -1) return queue.value[0] ?? null;
    const nextIndex = index + 1;
    if (nextIndex >= queue.value.length) {
      return wrap ? queue.value[0] : null;
    }
    return queue.value[nextIndex];
  }

  function next() {
    const nextSong = getNextSong(true);
    if (!nextSong) return;
    return playSong(nextSong, queue.value);
  }

  function previous() {
    if (!currentSong.value || queue.value.length === 0) return;
    const index = queue.value.findIndex((song) => song.id === currentSong.value?.id);
    const nextSong = queue.value[(index - 1 + queue.value.length) % queue.value.length];
    return playSong(nextSong, queue.value);
  }

  function addToQueue(song: Song) {
    ensureCurrentSongInQueue();
    if (!queue.value.some((item) => item.id === song.id)) {
      queue.value = [...queue.value, song];
    }
    if (!currentSong.value) {
      currentSong.value = song;
    }
  }

  function playNext(song: Song) {
    if (!currentSong.value) {
      return playSong(song, [song]);
    }
    if (currentSong.value.id === song.id) {
      ensureCurrentSongInQueue();
      return Promise.resolve(false);
    }

    ensureCurrentSongInQueue();
    const currentIndex = queue.value.findIndex((item) => item.id === currentSong.value?.id);
    const queueWithoutSong = queue.value.filter((item) => item.id !== song.id);
    if (currentIndex === -1) {
      queue.value = [currentSong.value, song, ...queueWithoutSong];
      return Promise.resolve(false);
    }
    const insertIndex = queueWithoutSong.findIndex((item) => item.id === currentSong.value?.id) + 1;
    queue.value = [
      ...queueWithoutSong.slice(0, insertIndex),
      song,
      ...queueWithoutSong.slice(insertIndex)
    ];
    return Promise.resolve(false);
  }

  function ensureCurrentSongInQueue() {
    if (currentSong.value && !queue.value.some((song) => song.id === currentSong.value?.id)) {
      queue.value = [currentSong.value, ...queue.value];
    }
  }

  function removeFromQueue(songId: number) {
    const removingCurrent = currentSong.value?.id === songId;
    const nextQueue = queue.value.filter((song) => song.id !== songId);
    queue.value = nextQueue;

    if (!removingCurrent) return;
    const nextSong = nextQueue[0] ?? null;
    if (nextSong) {
      void playSong(nextSong, nextQueue);
      return;
    }
    currentSong.value = null;
    isPlaying.value = false;
    currentTime.value = 0;
    duration.value = 0;
    errorMessage.value = "";
  }

  function clearQueue() {
    queue.value = [];
    currentSong.value = null;
    isPlaying.value = false;
    currentTime.value = 0;
    duration.value = 0;
    errorMessage.value = "";
  }

  function ensurePlaybackAllowed() {
    const auth = useAuthStore();
    if (auth.isAuthenticated) return true;
    const message = "请先登录后播放音乐";
    useUiStore().toast(message);
    setError(message);
    return false;
  }

  async function maybeRecordPlay() {
    const auth = useAuthStore();
    const song = currentSong.value;
    if (!auth.isAuthenticated || !song || currentTime.value < 30 || recordedSongIds.value.has(song.id)) {
      return;
    }
    recordedSongIds.value.add(song.id);
    try {
      await songApi.recordPlay(song.id, Math.floor(currentTime.value), "FRONTEND");
    } catch {
      // The global HTTP interceptor already handles user feedback.
    }
  }

  function dispatchPlayRequest(song: Song | null, time: number, shouldPlay: boolean) {
    if (!song || typeof window === "undefined") return Promise.resolve(false);

    return new Promise<boolean>((resolve) => {
      let settled = false;
      const respond = (played: boolean) => {
        if (settled) return;
        settled = true;
        resolve(played);
      };

      window.dispatchEvent(new CustomEvent(PLAYER_PLAY_REQUEST_EVENT, {
        detail: {
          song,
          time,
          shouldPlay,
          respond
        }
      }));

      window.setTimeout(() => respond(false), 4000);
    });
  }

  watch(
    currentSong,
    (song) => {
      if (song) {
        localStorage.setItem(PLAYER_SONG_KEY, JSON.stringify(song));
      } else {
        localStorage.removeItem(PLAYER_SONG_KEY);
      }
    },
    { deep: true }
  );

  watch(
    queue,
    (songs) => {
      localStorage.setItem(PLAYER_QUEUE_KEY, JSON.stringify(songs));
    },
    { deep: true }
  );

  watch(volume, (nextVolume) => {
    localStorage.setItem(PLAYER_VOLUME_KEY, String(nextVolume));
  });

  watch(playMode, (mode) => {
    localStorage.setItem(PLAYER_MODE_KEY, mode);
  });

  return {
    currentSong,
    queue,
    playMode,
    playModeLabel,
    isPlaying,
    isLoading,
    currentTime,
    duration,
    volume,
    errorMessage,
    seekTarget,
    seekRequestId,
    seekShouldPlay,
    progressPercent,
    playSong,
    replaceCurrentSong,
    setPlaying,
    setLoading,
    setTime,
    setVolume,
    setPlayMode,
    cyclePlayMode,
    setError,
    seekTo,
    resumeCurrent,
    getNextSong,
    next,
    previous,
    addToQueue,
    playNext,
    removeFromQueue,
    clearQueue
  };
});
