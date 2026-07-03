<template>
  <section class="song-library-page">
    <PageToolbar />

    <header class="song-library-head">
      <div>
        <p class="feature-label">MeloSpace</p>
        <h1 class="page-title">歌曲库</h1>
        <p>浏览当前可播放的全部歌曲，按歌手、专辑和热度快速定位。</p>
      </div>
      <div class="song-library-summary" aria-label="歌曲库统计">
        <span>
          <strong>{{ total }}</strong>
          首歌曲
        </span>
        <span>
          <strong>{{ currentStart }}-{{ currentEnd }}</strong>
          当前显示
        </span>
      </div>
    </header>

    <form class="compact-panel song-library-filter-panel" @submit.prevent="applyFiltersNow">
      <div class="list-controls song-library-controls">
        <label>
          <span>搜索</span>
          <input v-model.trim="filters.keyword" placeholder="歌曲名" />
        </label>
        <label>
          <span>歌手</span>
          <select v-model.number="filters.artistId">
            <option :value="0">全部歌手</option>
            <option v-for="artist in artists" :key="artist.id" :value="artist.id">{{ artist.name }}</option>
          </select>
        </label>
        <label>
          <span>专辑</span>
          <select v-model.number="filters.albumId" :disabled="!albumsForFilter.length">
            <option :value="0">全部专辑</option>
            <option v-for="album in albumsForFilter" :key="album.id" :value="album.id">{{ album.title }}</option>
          </select>
        </label>
        <label>
          <span>排序</span>
          <select v-model="filters.sort">
            <option value="updatedDesc">最近更新</option>
            <option value="createdDesc">最近创建</option>
            <option value="playsDesc">播放最多</option>
            <option value="titleAsc">歌曲 A-Z</option>
            <option value="durationDesc">时长从长到短</option>
            <option value="durationAsc">时长从短到长</option>
          </select>
        </label>
        <label>
          <span>每页</span>
          <select v-model.number="filters.size">
            <option :value="12">12 首</option>
            <option :value="20">20 首</option>
            <option :value="36">36 首</option>
          </select>
        </label>
        <div class="song-library-filter-actions">
          <button class="secondary-action" type="button" @click="resetFilters">重置</button>
          <button class="primary-action" type="submit">
            <Search :size="16" />
            筛选
          </button>
        </div>
      </div>
    </form>

    <section class="song-library-section">
      <div class="section-head song-library-list-head">
        <div>
          <h2>全部歌曲</h2>
          <span>{{ activeFilterLabel }}</span>
        </div>
        <div class="song-library-actions">
          <button class="secondary-action" type="button" :disabled="loading" @click="refreshSongs">
            <RefreshCw :size="16" />
            刷新
          </button>
          <button class="primary-action" type="button" :disabled="!songs.length" @click="playPage">
            <Play :size="16" fill="currentColor" />
            播放本页
          </button>
        </div>
      </div>

      <div v-if="songs.length" class="song-library-table" aria-live="polite">
        <div class="song-library-table-head">
          <span>歌曲</span>
          <span>专辑</span>
          <span>标签</span>
          <span>时长</span>
          <span>播放</span>
        </div>
        <div v-for="song in songs" :key="song.id" class="song-library-row">
          <SongRow
            class="song-library-row-song"
            :song="song"
            :is-current="player.currentSong?.id === song.id"
            :is-playing="player.isPlaying"
            @toggle-play="toggleSongPlayback"
            @open-player="openPlayer"
          />
          <RouterLink v-if="song.albumId" class="song-library-row-link" :to="`/albums/${song.albumId}`">
            {{ displayName(song.albumTitle, "未绑定专辑") }}
          </RouterLink>
          <span v-else class="muted-line">未绑定专辑</span>
          <span>{{ song.genre || song.language || song.mood || "未标注" }}</span>
          <span>{{ formatDuration(song.durationSeconds) }}</span>
          <span>{{ formatPlayCount(song.playCount) }}</span>
        </div>
      </div>

      <EmptyState v-else-if="loading">正在加载歌曲库...</EmptyState>
      <EmptyState v-else>{{ errorMessage || "暂无匹配歌曲。" }}</EmptyState>

      <div v-if="totalPages > 1" class="list-pagination">
        <button type="button" :disabled="page <= 1 || loading" @click="setPage(page - 1)">上一页</button>
        <span>{{ page }} / {{ totalPages }} · {{ total }} 首</span>
        <button type="button" :disabled="page >= totalPages || loading" @click="setPage(page + 1)">下一页</button>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { Play, RefreshCw, Search } from "lucide-vue-next";
import { albumApi, artistApi, songApi } from "@/api";
import type { Album, Artist, PageResult, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import SongRow from "@/components/SongRow.vue";
import { usePlayerStore } from "@/stores/player";
import { displayName, formatDuration } from "@/utils/format";

type SongLibrarySort = "updatedDesc" | "createdDesc" | "playsDesc" | "titleAsc" | "durationDesc" | "durationAsc";

const SONG_PAGE_CACHE_LIMIT = 60;
const player = usePlayerStore();
const router = useRouter();
const filters = reactive({
  keyword: "",
  artistId: 0,
  albumId: 0,
  sort: "updatedDesc" as SongLibrarySort,
  size: 20
});
const page = ref(1);
const total = ref(0);
const songs = ref<Song[]>([]);
const artists = ref<Artist[]>([]);
const albums = ref<Album[]>([]);
const loading = ref(false);
const errorMessage = ref("");
const pageCache = new Map<string, PageResult<Song>>();
const requestCache = new Map<string, Promise<PageResult<Song>>>();
let filterDebounceId: number | undefined;
let loadRunId = 0;

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / filters.size)));
const currentStart = computed(() => (total.value ? (page.value - 1) * filters.size + 1 : 0));
const currentEnd = computed(() => Math.min(page.value * filters.size, total.value));
const albumsForFilter = computed(() => {
  if (!filters.artistId) return albums.value;
  return albums.value.filter((album) => album.artistId === filters.artistId);
});
const activeFilterLabel = computed(() => {
  const parts: string[] = [];
  if (filters.keyword) parts.push(`关键词：${filters.keyword}`);
  if (filters.artistId) parts.push(`歌手：${artists.value.find((artist) => artist.id === filters.artistId)?.name ?? filters.artistId}`);
  if (filters.albumId) parts.push(`专辑：${albums.value.find((album) => album.id === filters.albumId)?.title ?? filters.albumId}`);
  return parts.length ? parts.join(" · ") : "显示全部已上架歌曲";
});

watch(
  () => filters.artistId,
  () => {
    if (filters.albumId && !albumsForFilter.value.some((album) => album.id === filters.albumId)) {
      filters.albumId = 0;
    }
  }
);

watch(
  () => [filters.keyword, filters.artistId, filters.albumId, filters.sort, filters.size],
  () => {
    page.value = 1;
    scheduleLoadSongs();
  }
);

watch(page, () => {
  void loadSongs();
});

onMounted(() => {
  void loadFilterOptions();
  void loadSongs();
});

async function loadFilterOptions() {
  const [artistList, albumList] = await Promise.all([artistApi.list(), albumApi.list()]);
  artists.value = artistList;
  albums.value = albumList;
}

function scheduleLoadSongs() {
  if (filterDebounceId) window.clearTimeout(filterDebounceId);
  filterDebounceId = window.setTimeout(() => {
    void loadSongs();
  }, 240);
}

function applyFiltersNow() {
  if (filterDebounceId) window.clearTimeout(filterDebounceId);
  void loadSongs();
}

function resetFilters() {
  filters.keyword = "";
  filters.artistId = 0;
  filters.albumId = 0;
  filters.sort = "updatedDesc";
  filters.size = 20;
  page.value = 1;
  applyFiltersNow();
}

function refreshSongs() {
  void loadSongs(true);
}

async function loadSongs(force = false) {
  const params = buildSongParams();
  const key = JSON.stringify(params);
  const runId = ++loadRunId;
  errorMessage.value = "";

  if (!force) {
    const cached = pageCache.get(key);
    if (cached) {
      applySongPage(cached);
      return;
    }
  }

  loading.value = true;
  try {
    const data = await readSongPage(key, params, force);
    if (runId === loadRunId) applySongPage(data);
  } catch {
    if (runId === loadRunId) {
      errorMessage.value = "歌曲库加载失败，请稍后重试。";
      songs.value = [];
      total.value = 0;
    }
  } finally {
    if (runId === loadRunId) loading.value = false;
  }
}

function buildSongParams() {
  return {
    page: page.value,
    size: filters.size,
    keyword: filters.keyword || undefined,
    artistId: filters.artistId || undefined,
    albumId: filters.albumId || undefined,
    sort: filters.sort
  };
}

async function readSongPage(key: string, params: ReturnType<typeof buildSongParams>, force: boolean) {
  if (!force) {
    const pending = requestCache.get(key);
    if (pending) return pending;
  }
  const request = songApi.list(params)
    .then((data) => {
      rememberSongPage(key, data);
      return data;
    })
    .finally(() => {
      requestCache.delete(key);
    });
  requestCache.set(key, request);
  return request;
}

function rememberSongPage(key: string, data: PageResult<Song>) {
  if (pageCache.has(key)) pageCache.delete(key);
  pageCache.set(key, data);
  while (pageCache.size > SONG_PAGE_CACHE_LIMIT) {
    const oldestKey = pageCache.keys().next().value;
    if (!oldestKey) break;
    pageCache.delete(oldestKey);
  }
}

function applySongPage(data: PageResult<Song>) {
  songs.value = data.items;
  total.value = data.total;
  const maxPage = Math.max(1, Math.ceil(data.total / filters.size));
  if (page.value > maxPage) page.value = maxPage;
}

function setPage(nextPage: number) {
  page.value = Math.min(Math.max(nextPage, 1), totalPages.value);
}

function playPage() {
  if (!songs.value.length) return;
  player.playSong(songs.value[0], songs.value);
}

function toggleSongPlayback(song: Song) {
  if (player.currentSong?.id === song.id) {
    if (player.isPlaying) {
      player.setPlaying(false);
    } else {
      void player.resumeCurrent();
    }
    return;
  }
  player.playSong(song, songs.value);
}

async function openPlayer(song: Song) {
  const played = await player.playSong(song, songs.value);
  if (played) router.push("/player");
}

function formatPlayCount(value?: number | null) {
  if (!value) return "0";
  if (value >= 10000) return `${(value / 10000).toFixed(1)}万`;
  return String(value);
}
</script>
