import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { Song } from "@/api/types";
import { songApi } from "@/api";
import { useAuthStore } from "@/stores/auth";

export const usePlayerStore = defineStore("player", () => {
  const currentSong = ref<Song | null>(null);
  const queue = ref<Song[]>([]);
  const isPlaying = ref(false);
  const isLoading = ref(false);
  const currentTime = ref(0);
  const duration = ref(0);
  const volume = ref(0.8);
  const errorMessage = ref("");
  const recordedSongIds = ref<Set<number>>(new Set());

  const progressPercent = computed(() => {
    if (!duration.value) return 0;
    return Math.min(100, Math.max(0, (currentTime.value / duration.value) * 100));
  });

  function playSong(song: Song, songs: Song[] = []) {
    currentSong.value = song;
    queue.value = songs.length ? songs : [song];
    isPlaying.value = true;
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

  function next() {
    if (!currentSong.value || queue.value.length === 0) return;
    const index = queue.value.findIndex((song) => song.id === currentSong.value?.id);
    const nextSong = queue.value[(index + 1 + queue.value.length) % queue.value.length];
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

  return {
    currentSong,
    queue,
    isPlaying,
    isLoading,
    currentTime,
    duration,
    volume,
    errorMessage,
    progressPercent,
    playSong,
    setPlaying,
    setLoading,
    setTime,
    setVolume,
    setError,
    next,
    previous
  };
});
