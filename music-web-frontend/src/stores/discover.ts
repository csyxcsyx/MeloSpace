import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { songApi } from "@/api";
import type { Song } from "@/api/types";

const DISCOVER_RECOMMENDATION_KEY = "melospace-discover-recommendation";
const DISCOVER_SCENE_KEY = "melospace-discover-scene";
const RECOMMENDATION_SIZE = 12;
const SCENE_RECOMMENDATION_SIZE = 8;
const JAY_CHOU_MINIMUM = 3;

interface RecommendationState {
  dateKey: string;
  refreshIndex: number;
}

export type SceneId = "study" | "night" | "workout" | "relax" | "heal" | "commute";

export interface SceneDefinition {
  id: SceneId;
  label: string;
  title: string;
  subtitle: string;
  accent: string;
  keywords: string[];
}

export const SCENE_DEFINITIONS: SceneDefinition[] = [
  {
    id: "study",
    label: "学习",
    title: "低干扰专注流",
    subtitle: "轻节奏、纯净声线，适合写代码和复习。",
    accent: "#3080ff",
    keywords: ["学习", "专注", "轻音乐", "纯音乐", "钢琴", "治愈", "流行", "安静", "慢歌", "study"]
  },
  {
    id: "night",
    label: "深夜",
    title: "夜色里的慢拍",
    subtitle: "把音量放低，留一点情绪和空间。",
    accent: "#625fff",
    keywords: ["深夜", "夜", "失眠", "安静", "孤独", "抒情", "慢歌", "情歌", "雨", "night"]
  },
  {
    id: "workout",
    label: "运动",
    title: "加速心跳补给",
    subtitle: "更高热度、更强律动，适合通勤快走或训练。",
    accent: "#00bb7f",
    keywords: ["运动", "跑步", "节奏", "摇滚", "电子", "快乐", "热血", "快歌", "舞曲", "workout"]
  },
  {
    id: "relax",
    label: "放松",
    title: "松弛一点也很好",
    subtitle: "温和旋律和熟悉声线，给大脑留白。",
    accent: "#fe6e00",
    keywords: ["放松", "休息", "治愈", "轻松", "民谣", "流行", "温柔", "慢歌", "relax"]
  },
  {
    id: "heal",
    label: "治愈",
    title: "给今天一点亮色",
    subtitle: "明亮、温暖、容易跟唱的疗愈片段。",
    accent: "#ff2357",
    keywords: ["治愈", "温暖", "快乐", "甜", "抒情", "流行", "晴天", "海", "heal"]
  },
  {
    id: "commute",
    label: "通勤",
    title: "路上的轻快歌单",
    subtitle: "开场快一点，路程也显得短一点。",
    accent: "#00a5ef",
    keywords: ["通勤", "城市", "旅行", "流行", "节奏", "轻快", "国语", "英文", "commute"]
  }
];

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

function pickRecommendedSongs(source: Song[], state: RecommendationState) {
  const seed = hashString(`${state.dateKey}:${state.refreshIndex}`);
  const selected: Song[] = [];
  const shuffledSongs = seededShuffle(source, seed);
  const jaySongs = seededShuffle(source.filter(isJayChouSong), seed ^ 0x9e3779b9);

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

function readSceneId(): SceneId {
  const stored = localStorage.getItem(DISCOVER_SCENE_KEY);
  return SCENE_DEFINITIONS.some((scene) => scene.id === stored) ? stored as SceneId : "study";
}

function normalize(value?: string | null) {
  return (value ?? "").toLowerCase();
}

function sceneScore(song: Song, scene: SceneDefinition) {
  const searchable = normalize([
    song.title,
    song.artistName,
    song.albumTitle,
    song.genre,
    song.mood,
    song.language
  ].filter(Boolean).join(" "));

  let score = 0;
  for (const keyword of scene.keywords) {
    if (searchable.includes(keyword.toLowerCase())) {
      score += keyword.length > 2 ? 8 : 5;
    }
  }

  if (song.mood && scene.keywords.some((keyword) => normalize(song.mood).includes(keyword.toLowerCase()))) {
    score += 12;
  }
  if (song.genre && scene.keywords.some((keyword) => normalize(song.genre).includes(keyword.toLowerCase()))) {
    score += 8;
  }
  if (scene.id === "workout" || scene.id === "commute") {
    score += Math.min(10, Math.log10(Math.max(song.playCount, 1)) * 3);
  }
  if ((scene.id === "night" || scene.id === "study" || scene.id === "relax") && normalize(song.genre).includes("摇滚")) {
    score -= 5;
  }

  return score;
}

function pickSceneSongs(source: Song[], scene: SceneDefinition, state: RecommendationState) {
  const seed = hashString(`${state.dateKey}:${state.refreshIndex}:${scene.id}`);
  const shuffled = seededShuffle(source, seed);
  const ranked = shuffled
    .map((song) => ({ song, score: sceneScore(song, scene) }))
    .sort((a, b) => b.score - a.score);

  const matched = ranked.filter((item) => item.score > 0).map((item) => item.song);
  const selected: Song[] = [];
  for (const song of matched) {
    if (selected.length >= SCENE_RECOMMENDATION_SIZE) break;
    addUniqueSong(selected, song);
  }
  for (const song of shuffled) {
    if (selected.length >= SCENE_RECOMMENDATION_SIZE) break;
    addUniqueSong(selected, song);
  }
  return selected;
}

export const useDiscoverStore = defineStore("discover", () => {
  const songs = ref<Song[]>([]);
  const loading = ref(false);
  const loaded = ref(false);
  const recommendationState = ref<RecommendationState>(readRecommendationState());
  const selectedSceneId = ref<SceneId>(readSceneId());
  let loadPromise: Promise<void> | null = null;

  const recommendedSongs = computed(() => pickRecommendedSongs(songs.value, recommendationState.value));
  const activeScene = computed(() => SCENE_DEFINITIONS.find((scene) => scene.id === selectedSceneId.value) ?? SCENE_DEFINITIONS[0]);
  const sceneRadioSongs = computed(() => pickSceneSongs(songs.value, activeScene.value, recommendationState.value));

  async function load(force = false) {
    syncRecommendationDate();
    if (loaded.value && !force) return;
    if (loadPromise) return loadPromise;

    loading.value = true;
    loadPromise = fetchAllSongs()
      .then((items) => {
        songs.value = items;
        loaded.value = true;
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
  }

  function setScene(sceneId: SceneId) {
    selectedSceneId.value = sceneId;
    localStorage.setItem(DISCOVER_SCENE_KEY, sceneId);
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
    activeScene,
    recommendedSongs,
    sceneRadioSongs,
    sceneDefinitions: SCENE_DEFINITIONS,
    load,
    refreshRecommendations,
    setScene
  };
});
