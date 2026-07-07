<template>
  <section class="profile-page">
    <header class="page-header">
      <h1 class="page-title">我的音乐</h1>
    </header>

    <section class="profile-grid">
      <div class="compact-panel">
        <div class="section-head">
          <h2>我的歌单</h2>
        </div>
        <form class="inline-form" @submit.prevent="createPlaylist">
          <input v-model.trim="playlistTitle" placeholder="新歌单名称" />
          <button type="submit">创建</button>
        </form>
        <div class="list-controls compact-list-controls">
          <label>
            <span>搜索</span>
            <input v-model.trim="playlistQuery" placeholder="歌单名" />
          </label>
          <label>
            <span>排序</span>
            <select v-model="playlistSort">
              <option value="updatedDesc">最近更新</option>
              <option value="titleAsc">名称 A-Z</option>
              <option value="songCountDesc">歌曲最多</option>
            </select>
          </label>
        </div>
        <div class="profile-list">
          <div v-for="playlist in pagedPlaylists" :key="playlist.id" class="profile-list-row playlist-profile-row">
            <RouterLink class="profile-list-main" :to="`/playlists/${playlist.id}`">
              <strong>{{ playlist.title }}</strong>
              <span>{{ playlist.songCount }} 首</span>
            </RouterLink>
            <button class="danger-icon-action playlist-delete-action" type="button" @click="deletePlaylist(playlist)">
              <Trash2 :size="16" />
              <span>删除</span>
            </button>
          </div>
          <p v-if="!filteredPlaylists.length" class="muted-line">还没有匹配的歌单。</p>
        </div>
        <div v-if="playlistPageCount > 1" class="list-pagination">
          <button type="button" :disabled="profilePages.playlists <= 1" @click="setProfilePage('playlists', profilePages.playlists - 1)">上一页</button>
          <span>{{ profilePages.playlists }} / {{ playlistPageCount }}</span>
          <button type="button" :disabled="profilePages.playlists >= playlistPageCount" @click="setProfilePage('playlists', profilePages.playlists + 1)">下一页</button>
        </div>
      </div>

      <div class="compact-panel">
        <div class="section-head">
          <h2>收藏</h2>
        </div>
        <div class="list-controls">
          <label>
            <span>搜索</span>
            <input v-model.trim="favoriteQuery" placeholder="歌曲或歌单" />
          </label>
          <label>
            <span>类型</span>
            <select v-model="favoriteTypeFilter">
              <option value="ALL">全部</option>
              <option value="SONG">歌曲</option>
              <option value="PLAYLIST">歌单</option>
            </select>
          </label>
          <label>
            <span>排序</span>
            <select v-model="favoriteSort">
              <option value="newest">最近收藏</option>
              <option value="titleAsc">名称 A-Z</option>
            </select>
          </label>
        </div>
        <div class="profile-list">
          <RouterLink v-for="favorite in pagedFavorites" :key="favorite.id" :to="favoritePath(favorite)">
            <strong>{{ favoriteTitle(favorite) }}</strong>
            <span>{{ favoriteSubtitle(favorite) }}</span>
          </RouterLink>
          <p v-if="!filteredFavorites.length" class="muted-line">还没有匹配的收藏内容。</p>
        </div>
        <div v-if="favoritePageCount > 1" class="list-pagination">
          <button type="button" :disabled="profilePages.favorites <= 1" @click="setProfilePage('favorites', profilePages.favorites - 1)">上一页</button>
          <span>{{ profilePages.favorites }} / {{ favoritePageCount }}</span>
          <button type="button" :disabled="profilePages.favorites >= favoritePageCount" @click="setProfilePage('favorites', profilePages.favorites + 1)">下一页</button>
        </div>
      </div>

      <div class="compact-panel">
        <div class="section-head">
          <h2>最近播放</h2>
        </div>
        <div class="list-controls">
          <label>
            <span>搜索</span>
            <input v-model.trim="recentQuery" placeholder="歌曲或歌手" />
          </label>
          <label>
            <span>排序</span>
            <select v-model="recentSort">
              <option value="latest">最近播放</option>
              <option value="titleAsc">歌曲 A-Z</option>
              <option value="artistAsc">歌手 A-Z</option>
            </select>
          </label>
        </div>
        <div class="profile-list">
          <RouterLink v-for="item in pagedRecent" :key="item.id" :to="`/songs/${item.song?.id || item.songId}`">
            <strong>{{ item.song?.title || `歌曲 #${item.songId}` }}</strong>
            <span>{{ item.song?.artistName || item.sourceType || "MeloSpace" }}</span>
          </RouterLink>
          <p v-if="!filteredRecent.length" class="muted-line">暂无匹配的最近播放。</p>
        </div>
        <div v-if="recentPageCount > 1" class="list-pagination">
          <button type="button" :disabled="profilePages.recent <= 1" @click="setProfilePage('recent', profilePages.recent - 1)">上一页</button>
          <span>{{ profilePages.recent }} / {{ recentPageCount }}</span>
          <button type="button" :disabled="profilePages.recent >= recentPageCount" @click="setProfilePage('recent', profilePages.recent + 1)">下一页</button>
        </div>
      </div>

      <div class="compact-panel">
        <div class="section-head">
          <h2>账号</h2>
        </div>
        <div class="profile-list">
          <p class="muted-line">{{ auth.user?.username }}</p>
          <button class="danger-action" type="button" @click="deleteMyAccount">注销账号</button>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { Trash2 } from "lucide-vue-next";
import { playlistApi, songApi, userApi } from "@/api";
import type { FavoriteItem, PageResult, PlayHistoryItem, Playlist, Song } from "@/api/types";
import { useAuthStore } from "@/stores/auth";
import { useUiStore } from "@/stores/ui";

interface FavoriteDisplayItem extends FavoriteItem {
  song?: Song | null;
  playlist?: Playlist | null;
}

interface RecentDisplayItem extends PlayHistoryItem {
  song?: Song | null;
}

const ui = useUiStore();
const auth = useAuthStore();
const router = useRouter();
const playlists = ref<Playlist[]>([]);
const favorites = ref<FavoriteDisplayItem[]>([]);
const recent = ref<RecentDisplayItem[]>([]);
const playlistTitle = ref("");
const playlistQuery = ref("");
const favoriteQuery = ref("");
const recentQuery = ref("");
const playlistSort = ref("updatedDesc");
const favoriteSort = ref("newest");
const recentSort = ref("latest");
const favoriteTypeFilter = ref<"ALL" | "SONG" | "PLAYLIST">("ALL");
const profilePages = reactive({
  playlists: 1,
  favorites: 1,
  recent: 1
});
const PROFILE_PAGE_SIZE = 6;

const filteredPlaylists = computed(() => {
  const query = normalizeSearch(playlistQuery.value);
  return [...playlists.value]
    .filter((playlist) => matchesQuery(`${playlist.title} ${playlist.description ?? ""}`, query))
    .sort((first, second) => {
      if (playlistSort.value === "titleAsc") return compareText(first.title, second.title);
      if (playlistSort.value === "songCountDesc") return second.songCount - first.songCount;
      return compareDate(second.updatedAt, first.updatedAt);
    });
});
const filteredFavorites = computed(() => {
  const query = normalizeSearch(favoriteQuery.value);
  return [...favorites.value]
    .filter((favorite) => favoriteTypeFilter.value === "ALL" || favorite.targetType === favoriteTypeFilter.value)
    .filter((favorite) => matchesQuery(`${favoriteTitle(favorite)} ${favoriteSubtitle(favorite)}`, query))
    .sort((first, second) => {
      if (favoriteSort.value === "titleAsc") return compareText(favoriteTitle(first), favoriteTitle(second));
      return compareDate(second.createdAt, first.createdAt);
    });
});
const filteredRecent = computed(() => {
  const query = normalizeSearch(recentQuery.value);
  return [...recent.value]
    .filter((item) => matchesQuery(`${item.song?.title ?? item.songId} ${item.song?.artistName ?? item.sourceType ?? ""}`, query))
    .sort((first, second) => {
      if (recentSort.value === "titleAsc") return compareText(first.song?.title ?? "", second.song?.title ?? "");
      if (recentSort.value === "artistAsc") return compareText(first.song?.artistName ?? "", second.song?.artistName ?? "");
      return compareDate(second.playedAt, first.playedAt);
    });
});
const pagedPlaylists = computed(() => paginate(filteredPlaylists.value, profilePages.playlists));
const pagedFavorites = computed(() => paginate(filteredFavorites.value, profilePages.favorites));
const pagedRecent = computed(() => paginate(filteredRecent.value, profilePages.recent));
const playlistPageCount = computed(() => pageCount(filteredPlaylists.value.length));
const favoritePageCount = computed(() => pageCount(filteredFavorites.value.length));
const recentPageCount = computed(() => pageCount(filteredRecent.value.length));

watch([playlistQuery, playlistSort], () => {
  profilePages.playlists = 1;
});

watch([favoriteQuery, favoriteSort, favoriteTypeFilter], () => {
  profilePages.favorites = 1;
});

watch([recentQuery, recentSort], () => {
  profilePages.recent = 1;
});

onMounted(loadProfile);

async function loadProfile() {
  const [playlistPage, favoritePage, recentPage] = await Promise.all([
    fetchAllPageItems((page, size) => userApi.playlists(page, size)),
    fetchAllPageItems((page, size) => userApi.favorites(page, size)),
    fetchAllPageItems((page, size) => userApi.recentPlays(page, size))
  ]);
  playlists.value = playlistPage;
  favorites.value = await hydrateFavorites(favoritePage);
  recent.value = await hydrateRecentPlays(recentPage);
}

async function fetchAllPageItems<T>(loader: (page: number, size: number) => Promise<PageResult<T>>) {
  const size = 100;
  const firstPage = await loader(1, size);
  const items = [...firstPage.items];
  const totalPages = Math.ceil(firstPage.total / size);
  for (let page = 2; page <= totalPages; page += 1) {
    const nextPage = await loader(page, size);
    items.push(...nextPage.items);
  }
  return items;
}

function setProfilePage(key: keyof typeof profilePages, page: number) {
  const maxPage = key === "playlists" ? playlistPageCount.value : key === "favorites" ? favoritePageCount.value : recentPageCount.value;
  profilePages[key] = Math.min(Math.max(page, 1), maxPage);
}

function paginate<T>(items: T[], page: number) {
  const start = (page - 1) * PROFILE_PAGE_SIZE;
  return items.slice(start, start + PROFILE_PAGE_SIZE);
}

function pageCount(total: number) {
  return Math.max(1, Math.ceil(total / PROFILE_PAGE_SIZE));
}

function normalizeSearch(value: string) {
  return value.trim().toLocaleLowerCase();
}

function matchesQuery(value: string, query: string) {
  if (!query) return true;
  return value.toLocaleLowerCase().includes(query);
}

function compareText(first: string, second: string) {
  return first.localeCompare(second, "zh-CN");
}

function compareDate(first: string, second: string) {
  return new Date(first).getTime() - new Date(second).getTime();
}

async function hydrateFavorites(items: FavoriteItem[]): Promise<FavoriteDisplayItem[]> {
  return Promise.all(
    items.map(async (favorite) => {
      if (favorite.targetType === "SONG" && !favorite.song) {
        try {
          return { ...favorite, song: await songApi.detail(favorite.targetId) };
        } catch {
          return favorite;
        }
      }
      if (favorite.targetType === "PLAYLIST" && !favorite.playlist) {
        try {
          return { ...favorite, playlist: await playlistApi.detail(favorite.targetId) };
        } catch {
          return favorite;
        }
      }
      return favorite;
    })
  );
}

async function hydrateRecentPlays(items: PlayHistoryItem[]): Promise<RecentDisplayItem[]> {
  return Promise.all(
    items.map(async (item) => {
      if (item.song) {
        return item;
      }
      try {
        return { ...item, song: await songApi.detail(item.songId) };
      } catch {
        return item;
      }
    })
  );
}

function favoritePath(favorite: FavoriteDisplayItem) {
  if (favorite.targetType === "PLAYLIST") {
    return `/playlists/${favorite.playlist?.id || favorite.targetId}`;
  }
  return `/songs/${favorite.song?.id || favorite.targetId}`;
}

function favoriteTitle(favorite: FavoriteDisplayItem) {
  if (favorite.targetType === "PLAYLIST") {
    return favorite.playlist?.title || `歌单 #${favorite.targetId}`;
  }
  return favorite.song?.title || `歌曲 #${favorite.targetId}`;
}

function favoriteSubtitle(favorite: FavoriteDisplayItem) {
  if (favorite.targetType === "PLAYLIST") {
    return favorite.playlist ? `${favorite.playlist.songCount} 首` : "歌单";
  }
  return favorite.song?.artistName || "歌曲";
}

async function createPlaylist() {
  if (!playlistTitle.value) return;
  await playlistApi.create({ title: playlistTitle.value, visibility: "PUBLIC" });
  playlistTitle.value = "";
  ui.toast("歌单已创建");
  await loadProfile();
}

async function deletePlaylist(playlist: Playlist) {
  if (!window.confirm(`确定删除歌单「${playlist.title}」吗？该操作不可恢复。`)) return;
  await playlistApi.remove(playlist.id);
  ui.toast("歌单已删除");
  await loadProfile();
  profilePages.playlists = Math.min(profilePages.playlists, playlistPageCount.value);
}

async function deleteMyAccount() {
  if (!window.confirm("确定注销当前账号吗？账号的歌单、收藏、评论和播放记录会一起删除。")) return;
  await auth.deleteAccount();
  ui.toast("账号已注销");
  router.push("/login");
}
</script>
