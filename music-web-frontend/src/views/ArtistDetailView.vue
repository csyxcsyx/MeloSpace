<template>
  <section>
    <PageToolbar />
    <EmptyState v-if="artistLoading">正在加载歌手...</EmptyState>
    <template v-else-if="artist">
      <header class="catalog-hero artist-catalog-hero">
        <div class="catalog-cover artist-cover">
          <img v-if="artistImage" :src="resolveMediaUrl(artistImage)" alt="" />
          <UserRound v-else :size="48" />
        </div>
        <div class="catalog-copy">
          <p class="feature-label">歌手</p>
          <h1>{{ artist.name }}</h1>
          <p>{{ artist.bio || "MeloSpace 曲库歌手" }}</p>
          <p class="catalog-meta">{{ songTotal }} 首歌 · {{ albums.length }} 张专辑</p>
          <div class="detail-actions">
            <button class="primary-action" type="button" :disabled="!songs.length" @click="playCurrentSongs">
              <Play :size="17" fill="currentColor" />
              播放当前歌曲
            </button>
          </div>
        </div>
      </header>

      <section>
        <div class="section-head artist-section-head">
          <div>
            <h2>全部专辑</h2>
            <span>{{ filteredAlbums.length }} 张 · 第 {{ albumPage }} / {{ albumTotalPages }} 页</span>
          </div>
        </div>
        <form class="compact-panel artist-filter-panel" @submit.prevent="applyAlbumFiltersNow">
          <div class="list-controls artist-album-controls">
            <label>
              <span>搜索专辑</span>
              <input v-model.trim="albumFilters.keyword" placeholder="专辑名" />
            </label>
            <label>
              <span>排序</span>
              <select v-model="albumFilters.sort">
                <option value="releaseDesc">发行时间新到旧</option>
                <option value="releaseAsc">发行时间旧到新</option>
                <option value="titleAsc">专辑 A-Z</option>
                <option value="updatedDesc">最近更新</option>
              </select>
            </label>
            <label>
              <span>每页</span>
              <select v-model.number="albumFilters.size">
                <option :value="4">4 张</option>
                <option :value="8">8 张</option>
                <option :value="12">12 张</option>
              </select>
            </label>
            <div class="song-library-filter-actions">
              <button class="secondary-action" type="button" @click="resetAlbumFilters">重置</button>
              <button class="primary-action" type="submit">
                <Search :size="16" />
                筛选
              </button>
            </div>
          </div>
        </form>
        <section v-if="pagedAlbums.length" class="artist-album-strip artist-album-paged">
          <RouterLink v-for="album in pagedAlbums" :key="album.id" class="artist-album-link" :to="`/albums/${album.id}`">
            <img v-if="album.coverUrl" :src="resolveMediaUrl(album.coverUrl)" alt="" />
            <Disc3 v-else :size="26" />
            <span>
              <strong>{{ album.title }}</strong>
              <small>{{ album.releaseDate || "发行日期未录入" }}</small>
            </span>
          </RouterLink>
        </section>
        <EmptyState v-else>暂无匹配专辑。</EmptyState>
        <div v-if="albumTotalPages > 1" class="list-pagination">
          <button type="button" :disabled="albumPage <= 1" @click="setAlbumPage(albumPage - 1)">上一页</button>
          <span>{{ albumPage }} / {{ albumTotalPages }} · {{ filteredAlbums.length }} 张</span>
          <button type="button" :disabled="albumPage >= albumTotalPages" @click="setAlbumPage(albumPage + 1)">下一页</button>
        </div>
      </section>

      <section>
        <div class="section-head artist-section-head">
          <div>
            <h2>全部歌曲</h2>
            <span>{{ songFilterLabel }}</span>
          </div>
        </div>
        <form class="compact-panel artist-filter-panel" @submit.prevent="applySongFiltersNow">
          <div class="list-controls artist-song-controls">
            <label>
              <span>搜索歌曲</span>
              <input v-model.trim="songFilters.keyword" placeholder="歌曲名" />
            </label>
            <label>
              <span>专辑</span>
              <select v-model.number="songFilters.albumId" :disabled="!albums.length">
                <option :value="0">全部专辑</option>
                <option v-for="album in albums" :key="album.id" :value="album.id">{{ album.title }}</option>
              </select>
            </label>
            <label>
              <span>排序</span>
              <select v-model="songFilters.sort">
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
              <select v-model.number="songFilters.size">
                <option :value="12">12 首</option>
                <option :value="20">20 首</option>
                <option :value="36">36 首</option>
              </select>
            </label>
            <div class="song-library-filter-actions">
              <button class="secondary-action" type="button" @click="resetSongFilters">重置</button>
              <button class="primary-action" type="submit">
                <Search :size="16" />
                筛选
              </button>
            </div>
          </div>
        </form>

        <SongColumnList
          v-if="songs.length"
          :songs="songs"
          :column-count="2"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else-if="songsLoading">正在加载歌曲...</EmptyState>
        <EmptyState v-else>{{ songError || "暂无匹配歌曲。" }}</EmptyState>
        <div v-if="songTotalPages > 1" class="list-pagination">
          <button type="button" :disabled="songPage <= 1 || songsLoading" @click="setSongPage(songPage - 1)">上一页</button>
          <span>{{ songPage }} / {{ songTotalPages }} · {{ songTotal }} 首</span>
          <button type="button" :disabled="songPage >= songTotalPages || songsLoading" @click="setSongPage(songPage + 1)">下一页</button>
        </div>
      </section>
    </template>
    <EmptyState v-else>没有找到这位歌手。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { Disc3, Play, Search, UserRound } from "lucide-vue-next";
import { albumApi, artistApi, songApi } from "@/api";
import type { Album, Artist, PageResult, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

type AlbumSort = "releaseDesc" | "releaseAsc" | "titleAsc" | "updatedDesc";
type SongSort = "updatedDesc" | "createdDesc" | "playsDesc" | "titleAsc" | "durationDesc" | "durationAsc";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
const artistLoading = ref(true);
const songsLoading = ref(false);
const artist = ref<Artist | null>(null);
const albums = ref<Album[]>([]);
const songs = ref<Song[]>([]);
const songTotal = ref(0);
const songError = ref("");
const loadedArtistId = ref<number | null>(null);
const albumPage = ref(1);
const songPage = ref(1);
let songDebounceId: number | undefined;
let songLoadRunId = 0;

const albumFilters = reactive({
  keyword: "",
  sort: "releaseDesc" as AlbumSort,
  size: 4
});
const songFilters = reactive({
  keyword: "",
  albumId: 0,
  sort: "updatedDesc" as SongSort,
  size: 20
});

const filteredAlbums = computed(() => {
  const keyword = albumFilters.keyword.toLowerCase();
  const source = keyword
    ? albums.value.filter((album) => album.title.toLowerCase().includes(keyword))
    : [...albums.value];
  return source.sort(compareAlbums);
});
const albumTotalPages = computed(() => Math.max(1, Math.ceil(filteredAlbums.value.length / albumFilters.size)));
const pagedAlbums = computed(() => {
  const start = (albumPage.value - 1) * albumFilters.size;
  return filteredAlbums.value.slice(start, start + albumFilters.size);
});
const songTotalPages = computed(() => Math.max(1, Math.ceil(songTotal.value / songFilters.size)));
const artistImage = computed(() => {
  return artist.value?.avatarUrl
    || albums.value.find((album) => album.coverUrl)?.coverUrl
    || songs.value.find((song) => song.coverUrl)?.coverUrl
    || "";
});
const songFilterLabel = computed(() => {
  const parts: string[] = [`第 ${songPage.value} / ${songTotalPages.value} 页`];
  if (songFilters.keyword) parts.push(`关键词：${songFilters.keyword}`);
  if (songFilters.albumId) parts.push(`专辑：${albums.value.find((album) => album.id === songFilters.albumId)?.title ?? songFilters.albumId}`);
  return parts.join(" · ");
});

onMounted(loadArtistPage);
onActivated(loadArtistPage);

watch(
  () => [route.name, route.params.id],
  () => {
    if (route.name === "artist-detail") {
      void loadArtistPage();
    }
  }
);

watch(
  () => [albumFilters.keyword, albumFilters.sort, albumFilters.size],
  () => {
    albumPage.value = 1;
  }
);

watch(
  () => [songFilters.keyword, songFilters.albumId, songFilters.sort, songFilters.size],
  () => {
    songPage.value = 1;
    scheduleLoadSongs();
  }
);

watch(songPage, () => {
  void loadSongs();
});

async function loadArtistPage() {
  if (route.name !== "artist-detail") return;
  const artistId = Number(route.params.id);
  if (!Number.isFinite(artistId)) {
    resetArtistState();
    artistLoading.value = false;
    return;
  }

  if (loadedArtistId.value === artistId && artist.value) {
    artistLoading.value = false;
    return;
  }

  loadedArtistId.value = artistId;
  artistLoading.value = true;
  resetFilters();
  try {
    const [artistList, albumList] = await Promise.all([
      artistApi.list({ page: 1, size: 500 }),
      albumApi.list({ artistId })
    ]);
    artist.value = artistList.find((item) => item.id === artistId) ?? null;
    albums.value = albumList;
    await loadSongs(true);
  } finally {
    artistLoading.value = false;
  }
}

function resetArtistState() {
  artist.value = null;
  albums.value = [];
  songs.value = [];
  songTotal.value = 0;
  loadedArtistId.value = null;
}

function resetFilters() {
  albumFilters.keyword = "";
  albumFilters.sort = "releaseDesc";
  albumFilters.size = 4;
  albumPage.value = 1;
  songFilters.keyword = "";
  songFilters.albumId = 0;
  songFilters.sort = "updatedDesc";
  songFilters.size = 20;
  songPage.value = 1;
}

function applyAlbumFiltersNow() {
  albumPage.value = 1;
}

function resetAlbumFilters() {
  albumFilters.keyword = "";
  albumFilters.sort = "releaseDesc";
  albumFilters.size = 4;
  albumPage.value = 1;
}

function setAlbumPage(nextPage: number) {
  albumPage.value = Math.min(Math.max(nextPage, 1), albumTotalPages.value);
}

function applySongFiltersNow() {
  if (songDebounceId) window.clearTimeout(songDebounceId);
  void loadSongs();
}

function resetSongFilters() {
  songFilters.keyword = "";
  songFilters.albumId = 0;
  songFilters.sort = "updatedDesc";
  songFilters.size = 20;
  songPage.value = 1;
  applySongFiltersNow();
}

function scheduleLoadSongs() {
  if (songDebounceId) window.clearTimeout(songDebounceId);
  songDebounceId = window.setTimeout(() => {
    void loadSongs();
  }, 220);
}

async function loadSongs(force = false) {
  const artistId = loadedArtistId.value;
  if (!artistId) return;
  const runId = ++songLoadRunId;
  songError.value = "";
  songsLoading.value = true;
  try {
    const data = await songApi.list({
      page: songPage.value,
      size: songFilters.size,
      artistId,
      albumId: songFilters.albumId || undefined,
      keyword: songFilters.keyword || undefined,
      sort: songFilters.sort
    });
    if (runId !== songLoadRunId && !force) return;
    applySongPage(data);
  } catch {
    if (runId === songLoadRunId) {
      songError.value = "歌曲加载失败，请稍后重试。";
      songs.value = [];
      songTotal.value = 0;
    }
  } finally {
    if (runId === songLoadRunId) songsLoading.value = false;
  }
}

function applySongPage(data: PageResult<Song>) {
  songs.value = data.items;
  songTotal.value = data.total;
  const maxPage = Math.max(1, Math.ceil(data.total / songFilters.size));
  if (songPage.value > maxPage) songPage.value = maxPage;
}

function setSongPage(nextPage: number) {
  songPage.value = Math.min(Math.max(nextPage, 1), songTotalPages.value);
}

function compareAlbums(a: Album, b: Album) {
  if (albumFilters.sort === "titleAsc") return a.title.localeCompare(b.title, "zh-Hans-CN");
  if (albumFilters.sort === "updatedDesc") return toTime(b.updatedAt) - toTime(a.updatedAt);
  if (albumFilters.sort === "releaseAsc") return toTime(a.releaseDate) - toTime(b.releaseDate);
  return toTime(b.releaseDate) - toTime(a.releaseDate);
}

function toTime(value?: string | null) {
  if (!value) return 0;
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : 0;
}

function playCurrentSongs() {
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
</script>
