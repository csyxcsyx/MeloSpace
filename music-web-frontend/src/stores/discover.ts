import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { songApi } from "@/api";
import type { Song } from "@/api/types";
import { resolveMediaUrl } from "@/utils/format";

const DISCOVER_RECOMMENDATION_KEY = "melospace-discover-recommendation";
const RECOMMENDATION_SIZE = 12;
const JAY_CHOU_MINIMUM = 3;
const ARTWORK_PRELOAD_LOOKAHEAD = [0, 1, 2];
const preloadedArtworkUrls = new Set<string>();

interface RecommendationState {
  dateKey: string;
  refreshIndex: number;
}

type WindowWithIdleCallback = Window & {
  requestIdleCallback?: (callback: IdleRequestCallback, options?: IdleRequestOptions) => number;
};

function todayKey() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function readRecommendationState(): RecommendationState {
  const fallback = { dateKey: todayKey(), refreshIndex: 0 };
  const raw = localStorage.getItem(DISCOVER_RECOMMENDATION_KEY);
  if (!raw) return fallback;

  try {
    const parsed = JSON.parse(raw) as Partial<RecommendationState>;
    if (parsed.dateKey === fallback.dateKey && Number.isInteger(parsed.refreshIndex)) {
      return { dateKey: fallback.dateKey, refreshIndex: parsed.refreshIndex ?? 0 };
    }
  } catch {
    localStorage.removeItem(DISCOVER_RECOMMENDATION_KEY);
  }
  return fallback;
}

function persistRecommendationState(state: RecommendationState) {
  localStorage.setItem(DISCOVER_RECOMMENDATION_KEY, JSON.stringify(state));
}

function hashString(value: string) {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function seededRandom(seed: number) {
  let state = seed >>> 0;
  return () => {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
    return state / 4294967296;
  };
}

function seededShuffle<T>(items: T[], seed: number) {
  const shuffled = [...items];
  const random = seededRandom(seed);
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(random() * (index + 1));
    [shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
  }
  return shuffled;
}

function isJayChouSong(song: Song) {
  return `${song.artistName ?? ""} ${song.title}`.includes("周杰伦");
}

function addUniqueSong(target: Song[], song: Song) {
  if (target.some((item) => item.id === song.id)) return false;
  target.push(song);
  return true;
}

function hasAlbumArtwork(song: Song) {
  return Boolean(song.coverUrl?.trim());
}

function pickRecommendedSongs(source: Song[], state: RecommendationState) {
  const artworkSongs = source.filter(hasAlbumArtwork);
  const seed = hashString(`${state.dateKey}:${state.refreshIndex}`);
  const selected: Song[] = [];
  const shuffledSongs = seededShuffle(artworkSongs, seed);
  const jaySongs = seededShuffle(artworkSongs.filter(isJayChouSong), seed ^ 0x9e3779b9);

  for (const song of jaySongs) {
    if (selected.length >= Math.min(JAY_CHOU_MINIMUM, jaySongs.length)) break;
    addUniqueSong(selected, song);
  }

  const usedArtists = new Set(selected.map((song) => song.artistId));
  for (const song of shuffledSongs) {
    if (selected.length >= RECOMMENDATION_SIZE) break;
    if (usedArtists.has(song.artistId)) continue;
    if (addUniqueSong(selected, song)) {
      usedArtists.add(song.artistId);
    }
  }

  for (const song of shuffledSongs) {
    if (selected.length >= RECOMMENDATION_SIZE) break;
    addUniqueSong(selected, song);
  }

  return selected;
}

function recommendationStateAtOffset(state: RecommendationState, offset: number) {
  return {
    dateKey: state.dateKey,
    refreshIndex: state.refreshIndex + offset
  };
}

function scheduleIdlePreload(callback: () => void) {
  if (typeof window === "undefined") return;
  const idleWindow = window as WindowWithIdleCallback;
  if (typeof idleWindow.requestIdleCallback === "function") {
    idleWindow.requestIdleCallback(callback, { timeout: 900 });
    return;
  }
  window.setTimeout(callback, 80);
}

function preloadSongArtwork(source: Song[], state: RecommendationState) {
  if (!source.length) return;
  const artworkUrls = ARTWORK_PRELOAD_LOOKAHEAD.flatMap((offset) => (
    pickRecommendedSongs(source, recommendationStateAtOffset(state, offset))
  ))
    .map((song) => resolveMediaUrl(song.coverUrl))
    .filter((url): url is string => Boolean(url && !preloadedArtworkUrls.has(url)));

  const uniqueArtworkUrls = [...new Set(artworkUrls)];
  if (!uniqueArtworkUrls.length) return;

  scheduleIdlePreload(() => {
    for (const url of uniqueArtworkUrls) {
      if (preloadedArtworkUrls.has(url)) continue;
      const image = new Image();
      image.decoding = "async";
      image.src = url;
      preloadedArtworkUrls.add(url);
    }
  });
}

export const useDiscoverStore = defineStore("discover", () => {
  const songs = ref<Song[]>([]);
  const loading = ref(false);
  const loaded = ref(false);
  const recommendationState = ref<RecommendationState>(readRecommendationState());
  let loadPromise: Promise<void> | null = null;

  const recommendedSongs = computed(() => pickRecommendedSongs(songs.value, recommendationState.value));

  async function load(force = false) {
    syncRecommendationDate();
    if (loaded.value && !force) return;
    if (loadPromise) return loadPromise;

    loading.value = true;
    loadPromise = fetchAllSongs()
      .then((items) => {
        songs.value = items;
        loaded.value = true;
        preloadSongArtwork(items, recommendationState.value);
      })
      .finally(() => {
        loading.value = false;
        loadPromise = null;
      });
    return loadPromise;
  }

  async function fetchAllSongs() {
    const firstPage = await songApi.list({ page: 1, size: 100 });
    const items = [...firstPage.items];
    const totalPages = Math.ceil(firstPage.total / firstPage.size);
    for (let page = 2; page <= totalPages; page += 1) {
      const result = await songApi.list({ page, size: 100 });
      items.push(...result.items);
    }
    return items;
  }

  function refreshRecommendations() {
    syncRecommendationDate();
    recommendationState.value = {
      dateKey: recommendationState.value.dateKey,
      refreshIndex: recommendationState.value.refreshIndex + 1
    };
    persistRecommendationState(recommendationState.value);
    preloadSongArtwork(songs.value, recommendationState.value);
  }

  function syncRecommendationDate() {
    const nextDateKey = todayKey();
    if (recommendationState.value.dateKey === nextDateKey) return;
    recommendationState.value = { dateKey: nextDateKey, refreshIndex: 0 };
    persistRecommendationState(recommendationState.value);
  }

  return {
    songs,
    loading,
    loaded,
    recommendedSongs,
    load,
    refreshRecommendations
  };
});
