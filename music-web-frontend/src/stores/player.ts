import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";
import type { Song } from "@/api/types";
import { songApi } from "@/api";
import { useAuthStore } from "@/stores/auth";

export const PLAYER_PLAY_REQUEST_EVENT = "melospace-player-play-request";

const PLAYER_SONG_KEY = "melospace-player-song";
const PLAYER_QUEUE_KEY = "melospace-player-queue";
const PLAYER_VOLUME_KEY = "melospace-player-volume";
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

export const usePlayerStore = defineStore("player", () => {
  const currentSong = ref<Song | null>(readJson<Song | null>(PLAYER_SONG_KEY, LEGACY_PLAYER_SONG_KEY, null));
  const queue = ref<Song[]>(readJson<Song[]>(PLAYER_QUEUE_KEY, LEGACY_PLAYER_QUEUE_KEY, []));
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

  function playSong(song: Song, songs: Song[] = []) {
    currentSong.value = song;
    queue.value = songs.length ? songs : [song];
    isPlaying.value = false;
    currentTime.value = 0;
    duration.value = 0;
    errorMessage.value = "";
    dispatchPlayRequest(song, 0, true);
  }

  function replaceCurrentSong(song: Song, songs: Song[] = queue.value) {
    currentSong.value = song;
    queue.value = songs.length ? songs : [song];
    isPlaying.value = false;
    currentTime.value = 0;
    duration.value = 0;
    errorMessage.value = "";
  }

  function setPlaying(value: boolean) {
    isPlaying.value = value;
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

  function setError(message: string) {
    errorMessage.value = message;
    isPlaying.value = false;
    isLoading.value = false;
  }

  function seekTo(time: number, shouldPlay = true) {
    seekTarget.value = Math.max(0, time);
    seekShouldPlay.value = shouldPlay;
    seekRequestId.value += 1;
    dispatchPlayRequest(currentSong.value, seekTarget.value, shouldPlay);
  }

  function getNextSong(wrap = true) {
    if (!currentSong.value || queue.value.length === 0) return null;
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
    playSong(nextSong, queue.value);
  }

  function previous() {
    if (!currentSong.value || queue.value.length === 0) return;
    const index = queue.value.findIndex((song) => song.id === currentSong.value?.id);
    const nextSong = queue.value[(index - 1 + queue.value.length) % queue.value.length];
    playSong(nextSong, queue.value);
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
    if (!song || typeof window === "undefined") return;
    window.dispatchEvent(new CustomEvent(PLAYER_PLAY_REQUEST_EVENT, {
      detail: {
        song,
        time,
        shouldPlay
      }
    }));
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

  return {
    currentSong,
    queue,
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
    setError,
    seekTo,
    getNextSong,
    next,
    previous
  };
});
