<template>
  <section class="search-page" aria-labelledby="search-page-title">
    <PageToolbar />
    <header class="search-page-heading">
      <p class="search-eyebrow">在 MeloSpace 里发现声音</p>
      <h1 id="search-page-title" class="page-title">搜索</h1>
    </header>

    <div class="search-sticky-surface" data-glass="regular">
      <form class="search-command" role="search" @submit.prevent="submitSearch">
        <label class="sr-only" for="community-search-input">搜索歌曲、歌手、专辑、歌单或用户</label>
        <div class="search-input-shell">
          <Search class="search-leading-icon" :size="21" aria-hidden="true" />
          <input
            id="community-search-input"
            v-model="keywordInput"
            maxlength="50"
            autocomplete="off"
            enterkeyhint="search"
            placeholder="歌曲、歌手、专辑、歌单或用户"
            :aria-expanded="searchPanelOpen"
            aria-controls="search-assist-panel"
            @focus="openSearchPanel"
            @blur="closeSearchPanel"
            @keydown.esc="searchFocused = false"
          />
          <button
            v-if="keywordInput"
            class="search-clear-button"
            type="button"
            aria-label="清空搜索词"
            @mousedown.prevent
            @click="clearKeyword"
          >
            <X :size="18" aria-hidden="true" />
          </button>
          <button class="search-submit-button" type="submit" :disabled="!normalizedInput">
            搜索
          </button>
        </div>

        <div
          v-if="searchPanelOpen"
          id="search-assist-panel"
          class="search-assist-panel"
          data-glass="regular"
          @mousedown.prevent
        >
          <template v-if="normalizedInput">
            <div class="search-assist-heading">
              <span>搜索建议</span>
              <span v-if="suggestionsLoading" class="search-assist-status">加载中…</span>
            </div>
            <div v-if="suggestions.length" class="search-suggestion-list">
              <button
                v-for="suggestion in suggestions"
                :key="`${suggestion.type}-${suggestion.id}`"
                class="search-suggestion"
                type="button"
                @click="selectSuggestion(suggestion)"
              >
                <span class="search-suggestion-image" :class="{ 'is-user': suggestion.type === 'USER' }">
                  <img
                    v-if="suggestion.imageUrl"
                    :src="resolveMediaUrl(suggestion.imageUrl)"
                    alt=""
                  />
                  <component v-else :is="suggestionIcon(suggestion.type)" :size="18" aria-hidden="true" />
                </span>
                <span class="search-suggestion-copy">
                  <strong>{{ suggestion.title }}</strong>
                  <small>{{ suggestion.subtitle || suggestionTypeLabel(suggestion.type) }}</small>
                </span>
                <span class="search-suggestion-kind">{{ suggestionTypeLabel(suggestion.type) }}</span>
              </button>
            </div>
            <p v-else-if="!suggestionsLoading" class="search-assist-empty">
              按回车搜索“{{ normalizedInput }}”
            </p>
          </template>
          <template v-else>
            <div class="search-assist-heading">
              <span>最近搜索</span>
              <button v-if="searchHistory.length" type="button" @click="clearAllHistory">清除全部</button>
            </div>
            <div v-if="searchHistory.length" class="search-history-list">
              <div v-for="item in searchHistory" :key="item" class="search-history-row">
                <button class="search-history-main" type="button" @click="searchHistoryItem(item)">
                  <Clock3 :size="17" aria-hidden="true" />
                  <span>{{ item }}</span>
                </button>
                <button
                  class="search-history-remove"
                  type="button"
                  :aria-label="`删除搜索记录 ${item}`"
                  @click="removeHistoryItem(item)"
                >
                  <X :size="16" aria-hidden="true" />
                </button>
              </div>
            </div>
            <p v-else class="search-assist-empty">搜索过的内容会保存在这台设备上。</p>
          </template>
        </div>
      </form>
    </div>

    <nav v-if="activeKeyword" class="search-tabs" aria-label="搜索结果类型">
      <button
        v-for="tab in searchTabs"
        :key="tab.id"
        type="button"
        :class="{ active: activeType === tab.id }"
        :aria-current="activeType === tab.id ? 'page' : undefined"
        @click="setSearchType(tab.id)"
      >
        <span>{{ tab.label }}</span>
        <span class="search-tab-count">{{ formatCount(tab.count) }}</span>
      </button>
    </nav>

    <div v-if="errorMessage" class="search-feedback-panel search-error-panel" role="alert">
      <div>
        <strong>搜索暂时没有响应</strong>
        <p>{{ errorMessage }}</p>
      </div>
      <button class="secondary-action" type="button" @click="retrySearch">
        <RotateCcw :size="17" aria-hidden="true" />
        重试
      </button>
    </div>

    <div v-else-if="loading" class="search-loading-state" aria-live="polite" aria-busy="true">
      <span class="search-loading-orb" aria-hidden="true" />
      <span>正在为你整理结果…</span>
    </div>

    <section v-else-if="!activeKeyword" class="search-welcome" aria-labelledby="search-welcome-title">
      <div class="search-welcome-copy">
        <span class="search-welcome-icon"><Music2 :size="26" aria-hidden="true" /></span>
        <div>
          <h2 id="search-welcome-title">从一个名字或心情开始</h2>
          <p>试试歌曲、歌手、专辑、公开歌单，也可以找到社区里的创作者。</p>
        </div>
      </div>
      <div class="search-starter-list" aria-label="推荐搜索">
        <button v-for="starter in searchStarters" :key="starter" type="button" @click="searchHistoryItem(starter)">
          {{ starter }}
        </button>
      </div>
      <div v-if="searchHistory.length" class="search-recent-section">
        <div class="section-head search-section-head">
          <h2>最近搜索</h2>
          <button type="button" @click="clearAllHistory">清除全部</button>
        </div>
        <div class="search-recent-chips">
          <button v-for="item in searchHistory" :key="item" type="button" @click="searchHistoryItem(item)">
            <Clock3 :size="15" aria-hidden="true" />
            {{ item }}
          </button>
        </div>
      </div>
    </section>

    <template v-else-if="activeType === 'all' && summary">
      <section v-if="summary.songs.length" class="search-result-section" aria-labelledby="search-songs-heading">
        <div class="section-head search-section-head">
          <div>
            <h2 id="search-songs-heading">歌曲</h2>
            <span>{{ formatCount(summary.totals.songs) }} 个结果</span>
          </div>
          <button v-if="summary.totals.songs > summary.songs.length" type="button" @click="setSearchType('songs')">
            查看全部 <ArrowRight :size="16" aria-hidden="true" />
          </button>
        </div>
        <div class="search-song-list">
          <SearchSongRow v-for="song in summary.songs" :key="song.id" :song="song" />
        </div>
      </section>

      <div v-if="hasAnySummaryResult" class="search-overview-grid">
        <section v-if="summary.artists.length" class="search-result-panel">
          <div class="section-head search-section-head">
            <div>
              <h2>歌手</h2>
              <span>{{ formatCount(summary.totals.artists) }} 个结果</span>
            </div>
            <button v-if="summary.totals.artists > summary.artists.length" type="button" @click="setSearchType('artists')">
              全部 <ArrowRight :size="16" aria-hidden="true" />
            </button>
          </div>
          <div class="search-entity-list">
            <RouterLink
              v-for="artist in summary.artists"
              :key="artist.id"
              class="search-entity-row"
              :to="`/artists/${artist.id}`"
            >
              <ResultImage :url="artist.avatarUrl" shape="circle" :fallback="UserRound" />
              <span class="search-entity-copy">
                <strong><HighlightText :text="artist.name" /></strong>
                <small><HighlightText :text="artist.bio || '歌手'" /></small>
              </span>
            </RouterLink>
          </div>
        </section>

        <section v-if="summary.albums.length" class="search-result-panel">
          <div class="section-head search-section-head">
            <div>
              <h2>专辑</h2>
              <span>{{ formatCount(summary.totals.albums) }} 个结果</span>
            </div>
            <button v-if="summary.totals.albums > summary.albums.length" type="button" @click="setSearchType('albums')">
              全部 <ArrowRight :size="16" aria-hidden="true" />
            </button>
          </div>
          <div class="search-entity-list">
            <RouterLink
              v-for="album in summary.albums"
              :key="album.id"
              class="search-entity-row"
              :to="`/albums/${album.id}`"
            >
              <ResultImage :url="album.coverUrl" :fallback="Disc3" />
              <span class="search-entity-copy">
                <strong><HighlightText :text="album.title" /></strong>
                <small><HighlightText :text="album.artistName || '专辑'" /></small>
              </span>
            </RouterLink>
          </div>
        </section>

        <section v-if="summary.playlists.length" class="search-result-panel">
          <div class="section-head search-section-head">
            <div>
              <h2>歌单</h2>
              <span>{{ formatCount(summary.totals.playlists) }} 个结果</span>
            </div>
            <button v-if="summary.totals.playlists > summary.playlists.length" type="button" @click="setSearchType('playlists')">
              全部 <ArrowRight :size="16" aria-hidden="true" />
            </button>
          </div>
          <div class="search-entity-list">
            <RouterLink
              v-for="playlist in summary.playlists"
              :key="playlist.id"
              class="search-entity-row"
              :to="`/playlists/${playlist.id}`"
            >
              <ResultImage :url="playlist.coverUrl" :fallback="ListMusic" />
              <span class="search-entity-copy">
                <strong><HighlightText :text="playlist.title" /></strong>
                <small>{{ playlist.songCount }} 首 · {{ formatCount(playlist.favoriteCount) }} 人收藏</small>
              </span>
            </RouterLink>
          </div>
        </section>

        <section v-if="summary.users.length" class="search-result-panel">
          <div class="section-head search-section-head">
            <div>
              <h2>用户</h2>
              <span>{{ formatCount(summary.totals.users) }} 个结果</span>
            </div>
            <button v-if="summary.totals.users > summary.users.length" type="button" @click="setSearchType('users')">
              全部 <ArrowRight :size="16" aria-hidden="true" />
            </button>
          </div>
          <div class="search-entity-list">
            <RouterLink
              v-for="user in summary.users"
              :key="user.id"
              class="search-entity-row"
              :to="`/users/${user.id}`"
            >
              <ResultImage :url="user.avatarUrl" shape="circle" :fallback="UserRound" />
              <span class="search-entity-copy">
                <strong><HighlightText :text="user.nickname" /></strong>
                <small><HighlightText :text="user.bio || `${user.publicPlaylistCount} 个公开歌单`" /></small>
              </span>
            </RouterLink>
          </div>
        </section>
      </div>

      <SearchEmpty v-if="!hasAnySummaryResult" />
    </template>

    <template v-else-if="categoryResult">
      <section class="search-result-section" :aria-labelledby="`search-${activeType}-heading`">
        <div class="section-head search-section-head search-category-heading">
          <div>
            <h2 :id="`search-${activeType}-heading`">{{ activeTabLabel }}</h2>
            <span>共 {{ formatCount(categoryResult.total) }} 个结果</span>
          </div>
        </div>

        <div v-if="categorySongs.length" class="search-song-list">
          <SearchSongRow v-for="song in categorySongs" :key="song.id" :song="song" />
        </div>

        <div v-else-if="categoryArtists.length" class="search-category-grid">
          <RouterLink
            v-for="artist in categoryArtists"
            :key="artist.id"
            class="search-category-card"
            :to="`/artists/${artist.id}`"
          >
            <ResultImage :url="artist.avatarUrl" shape="circle" size="large" :fallback="UserRound" />
            <span>
              <strong><HighlightText :text="artist.name" /></strong>
              <small><HighlightText :text="artist.bio || '歌手'" /></small>
            </span>
          </RouterLink>
        </div>

        <div v-else-if="categoryAlbums.length" class="search-category-grid">
          <RouterLink
            v-for="album in categoryAlbums"
            :key="album.id"
            class="search-category-card"
            :to="`/albums/${album.id}`"
          >
            <ResultImage :url="album.coverUrl" size="large" :fallback="Disc3" />
            <span>
              <strong><HighlightText :text="album.title" /></strong>
              <small><HighlightText :text="album.artistName || '专辑'" /></small>
            </span>
          </RouterLink>
        </div>

        <div v-else-if="categoryPlaylists.length" class="search-category-grid">
          <RouterLink
            v-for="playlist in categoryPlaylists"
            :key="playlist.id"
            class="search-category-card"
            :to="`/playlists/${playlist.id}`"
          >
            <ResultImage :url="playlist.coverUrl" size="large" :fallback="ListMusic" />
            <span>
              <strong><HighlightText :text="playlist.title" /></strong>
              <small>{{ playlist.songCount }} 首 · {{ formatCount(playlist.favoriteCount) }} 人收藏</small>
            </span>
          </RouterLink>
        </div>

        <div v-else-if="categoryUsers.length" class="search-user-grid">
          <RouterLink
            v-for="user in categoryUsers"
            :key="user.id"
            class="search-user-card"
            :to="`/users/${user.id}`"
          >
            <ResultImage :url="user.avatarUrl" shape="circle" size="large" :fallback="UserRound" />
            <span class="search-user-card-copy">
              <strong><HighlightText :text="user.nickname" /></strong>
              <small><HighlightText :text="user.bio || 'MeloSpace 用户'" /></small>
              <span>{{ user.publicPlaylistCount }} 个公开歌单 · {{ formatCount(user.receivedFavoriteCount) }} 次收藏</span>
            </span>
          </RouterLink>
        </div>

        <SearchEmpty v-else />
      </section>

      <nav v-if="pageCount > 1" class="search-pagination" aria-label="搜索结果分页">
        <button type="button" :disabled="currentPage <= 1" @click="setPage(currentPage - 1)">
          <ChevronLeft :size="18" aria-hidden="true" />
          上一页
        </button>
        <span aria-live="polite">第 {{ currentPage }} / {{ pageCount }} 页</span>
        <button type="button" :disabled="currentPage >= pageCount" @click="setPage(currentPage + 1)">
          下一页
          <ChevronRight :size="18" aria-hidden="true" />
        </button>
      </nav>
    </template>
  </section>
</template>

<script setup lang="ts">
import {
  computed,
  defineComponent,
  h,
  markRaw,
  onBeforeUnmount,
  ref,
  watch,
  type Component,
  type PropType
} from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import {
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  CirclePlay,
  Clock3,
  Disc3,
  ListMusic,
  Music2,
  Pause,
  RotateCcw,
  Search,
  UserRound,
  X
} from "lucide-vue-next";
import { searchApi } from "@/api";
import type {
  Album,
  Artist,
  PageResult,
  Playlist,
  PublicUserProfile,
  SearchResultItemMap,
  SearchResultType,
  SearchSuggestion,
  SearchSuggestionType,
  SearchTab,
  Song
} from "@/api/types";
import PageToolbar from "@/components/PageToolbar.vue";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";
import {
  clearSearchHistory,
  createLatestRequestGate,
  readSearchHistory,
  rememberSearch,
  removeSearchHistory,
  splitHighlight
} from "@/utils/search";

type SearchResultItem = SearchResultItemMap[SearchResultType];

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
const keywordInput = ref("");
const activeKeyword = ref("");
const activeType = ref<SearchTab>("all");
const currentPage = ref(1);
const summary = ref<Awaited<ReturnType<typeof searchApi.all>> | null>(null);
const categoryResult = ref<PageResult<SearchResultItem> | null>(null);
const loading = ref(false);
const errorMessage = ref("");
const searchFocused = ref(false);
const suggestions = ref<SearchSuggestion[]>([]);
const suggestionsLoading = ref(false);
const searchHistory = ref(readSearchHistory());
const searchGate = createLatestRequestGate();
const suggestionGate = createLatestRequestGate();
const SEARCH_PAGE_SIZE = 20;
const searchStarters = ["华语流行", "轻音乐", "摇滚", "治愈"];
const validSearchTypes: SearchTab[] = ["all", "songs", "artists", "albums", "playlists", "users"];
let suggestionTimer: number | undefined;
let blurTimer: number | undefined;
let searchAbortController: AbortController | undefined;
let suggestionAbortController: AbortController | undefined;

const normalizedInput = computed(() => keywordInput.value.trim().replace(/\s+/g, " ").slice(0, 50));
const searchPanelOpen = computed(() => searchFocused.value && (Boolean(normalizedInput.value) || searchHistory.value.length > 0));
const categorySongs = computed(() => activeType.value === "songs" ? categoryResult.value?.items as Song[] ?? [] : []);
const categoryArtists = computed(() => activeType.value === "artists" ? categoryResult.value?.items as Artist[] ?? [] : []);
const categoryAlbums = computed(() => activeType.value === "albums" ? categoryResult.value?.items as Album[] ?? [] : []);
const categoryPlaylists = computed(() => activeType.value === "playlists" ? categoryResult.value?.items as Playlist[] ?? [] : []);
const categoryUsers = computed(() => activeType.value === "users" ? categoryResult.value?.items as PublicUserProfile[] ?? [] : []);
const pageCount = computed(() => Math.max(1, Math.ceil((categoryResult.value?.total ?? 0) / SEARCH_PAGE_SIZE)));
const hasAnySummaryResult = computed(() => {
  if (!summary.value) return false;
  return Object.values(summary.value.totals).some((count) => count > 0);
});
const searchTabs = computed(() => {
  const totals = summary.value?.totals;
  const values = {
    songs: totals?.songs ?? (activeType.value === "songs" ? categoryResult.value?.total ?? 0 : 0),
    artists: totals?.artists ?? (activeType.value === "artists" ? categoryResult.value?.total ?? 0 : 0),
    albums: totals?.albums ?? (activeType.value === "albums" ? categoryResult.value?.total ?? 0 : 0),
    playlists: totals?.playlists ?? (activeType.value === "playlists" ? categoryResult.value?.total ?? 0 : 0),
    users: totals?.users ?? (activeType.value === "users" ? categoryResult.value?.total ?? 0 : 0)
  };
  return [
    { id: "all" as const, label: "综合", count: Object.values(values).reduce((sum, count) => sum + count, 0) },
    { id: "songs" as const, label: "歌曲", count: values.songs },
    { id: "artists" as const, label: "歌手", count: values.artists },
    { id: "albums" as const, label: "专辑", count: values.albums },
    { id: "playlists" as const, label: "歌单", count: values.playlists },
    { id: "users" as const, label: "用户", count: values.users }
  ];
});
const activeTabLabel = computed(() => searchTabs.value.find((tab) => tab.id === activeType.value)?.label ?? "搜索结果");
const displayedSongQueue = computed(() => activeType.value === "all" ? summary.value?.songs ?? [] : categorySongs.value);

const HighlightText = defineComponent({
  name: "HighlightText",
  props: {
    text: {
      type: String,
      default: ""
    }
  },
  setup(props) {
    return () => splitHighlight(props.text, activeKeyword.value).map((segment, index) =>
      segment.matched
        ? h("mark", { key: index, class: "search-highlight" }, segment.text)
        : h("span", { key: index }, segment.text)
    );
  }
});

const ResultImage = defineComponent({
  name: "ResultImage",
  props: {
    url: {
      type: String as PropType<string | null>,
      default: null
    },
    shape: {
      type: String as PropType<"square" | "circle">,
      default: "square"
    },
    size: {
      type: String as PropType<"default" | "large">,
      default: "default"
    },
    fallback: {
      type: Object as PropType<Component>,
      required: true
    }
  },
  setup(props) {
    return () => h("span", {
      class: [
        "search-result-image",
        props.shape === "circle" && "is-circle",
        props.size === "large" && "is-large"
      ]
    }, props.url
      ? h("img", { src: resolveMediaUrl(props.url), alt: "" })
      : h(props.fallback, { size: props.size === "large" ? 25 : 18, "aria-hidden": "true" }));
  }
});

const SearchSongRow = defineComponent({
  name: "SearchSongRow",
  props: {
    song: {
      type: Object as PropType<Song>,
      required: true
    }
  },
  setup(props) {
    return () => h("article", { class: "search-song-row" }, [
      h("button", {
        type: "button",
        class: "search-song-play",
        "aria-label": player.currentSong?.id === props.song.id && player.isPlaying
          ? `暂停 ${props.song.title}`
          : `播放 ${props.song.title}`,
        onClick: () => toggleSongPlayback(props.song)
      }, [
        props.song.coverUrl
          ? h("img", { src: resolveMediaUrl(props.song.coverUrl), alt: "" })
          : h(Music2, { size: 20, "aria-hidden": "true" }),
        h("span", { class: "search-song-play-overlay" }, [
          h(player.currentSong?.id === props.song.id && player.isPlaying ? Pause : CirclePlay, {
            size: 23,
            fill: "currentColor",
            "aria-hidden": "true"
          })
        ])
      ]),
      h(RouterLink, {
        class: "search-song-copy",
        to: `/songs/${props.song.id}`
      }, {
        default: () => [
          h("strong", null, splitHighlight(props.song.title, activeKeyword.value).map((segment, index) =>
            segment.matched
              ? h("mark", { key: index, class: "search-highlight" }, segment.text)
              : h("span", { key: index }, segment.text)
          )),
          h("small", null, [
            ...splitHighlight(props.song.artistName || "未知歌手", activeKeyword.value).map((segment, index) =>
              segment.matched
                ? h("mark", { key: `artist-${index}`, class: "search-highlight" }, segment.text)
                : h("span", { key: `artist-${index}` }, segment.text)
            ),
            props.song.albumTitle ? ` · ${props.song.albumTitle}` : ""
          ])
        ]
      }),
      h("span", { class: "search-song-plays" }, `${formatCount(props.song.playCount)} 次播放`)
    ]);
  }
});

const SearchEmpty = defineComponent({
  name: "SearchEmpty",
  setup() {
    return () => h("div", { class: "search-empty-state" }, [
      h(Search, { size: 25, "aria-hidden": "true" }),
      h("strong", null, "没有找到匹配内容"),
      h("p", null, "试试缩短关键词、检查拼写，或搜索歌手和专辑名称。"),
      h("div", { class: "search-empty-actions" }, searchStarters.map((starter) =>
        h("button", {
          type: "button",
          onClick: () => searchHistoryItem(starter)
        }, starter)
      ))
    ]);
  }
});

watch(
  () => route.fullPath,
  () => {
    if (route.name !== "search") return;
    syncFromRoute();
  },
  { immediate: true }
);

watch(keywordInput, (value) => {
  scheduleSuggestions(value);
});

onBeforeUnmount(() => {
  searchGate.invalidate();
  suggestionGate.invalidate();
  searchAbortController?.abort();
  suggestionAbortController?.abort();
  if (suggestionTimer !== undefined) window.clearTimeout(suggestionTimer);
  if (blurTimer !== undefined) window.clearTimeout(blurTimer);
});

function syncFromRoute() {
  const rawKeyword = firstQueryValue(route.query.keyword).trim().replace(/\s+/g, " ");
  const keyword = rawKeyword.slice(0, 50);
  const routeType = firstQueryValue(route.query.type) as SearchTab;
  const type = validSearchTypes.includes(routeType) ? routeType : "all";
  const page = Math.max(1, Number.parseInt(firstQueryValue(route.query.page), 10) || 1);

  keywordInput.value = keyword;
  activeKeyword.value = keyword;
  activeType.value = type;
  currentPage.value = type === "all" ? 1 : page;
  searchFocused.value = false;
  suggestions.value = [];
  void loadSearch();
}

function firstQueryValue(value: unknown) {
  if (Array.isArray(value)) return String(value[0] ?? "");
  return String(value ?? "");
}

function submitSearch() {
  const keyword = normalizedInput.value;
  if (!keyword) return;
  searchFocused.value = false;
  searchHistory.value = rememberSearch(keyword);
  void navigateToSearch(keyword, "all", 1);
}

function navigateToSearch(keyword: string, type: SearchTab, page: number) {
  return router.push({
    name: "search",
    query: {
      keyword,
      type,
      page: String(type === "all" ? 1 : page)
    }
  });
}

function setSearchType(type: SearchTab) {
  if (!activeKeyword.value || type === activeType.value) return;
  void navigateToSearch(activeKeyword.value, type, 1);
}

function setPage(page: number) {
  if (activeType.value === "all") return;
  const nextPage = Math.min(Math.max(page, 1), pageCount.value);
  if (nextPage === currentPage.value) return;
  void navigateToSearch(activeKeyword.value, activeType.value, nextPage);
}

async function loadSearch() {
  const keyword = activeKeyword.value;
  const type = activeType.value;
  const page = currentPage.value;
  const runId = searchGate.begin();
  searchAbortController?.abort();
  searchAbortController = new AbortController();
  const signal = searchAbortController.signal;
  errorMessage.value = "";
  summary.value = null;
  categoryResult.value = null;

  if (!keyword) {
    loading.value = false;
    return;
  }

  loading.value = true;
  try {
    if (type === "all") {
      const data = await searchApi.all(keyword, signal);
      if (!searchGate.isCurrent(runId)) return;
      summary.value = data;
    } else {
      const [summaryData, categoryData] = await Promise.all([
        searchApi.all(keyword, signal),
        searchApi.byType(type, keyword, page, SEARCH_PAGE_SIZE, signal)
      ]);
      if (!searchGate.isCurrent(runId)) return;
      summary.value = summaryData;
      categoryResult.value = categoryData as PageResult<SearchResultItem>;
      const maxPage = Math.max(1, Math.ceil(categoryData.total / SEARCH_PAGE_SIZE));
      if (page > maxPage) {
        void navigateToSearch(keyword, type, maxPage);
        return;
      }
    }
    searchHistory.value = rememberSearch(keyword);
  } catch {
    if (!searchGate.isCurrent(runId)) return;
    errorMessage.value = "请检查网络连接后重试，你当前的关键词和筛选条件不会丢失。";
  } finally {
    if (searchGate.isCurrent(runId)) loading.value = false;
  }
}

function retrySearch() {
  void loadSearch();
}

function openSearchPanel() {
  if (blurTimer !== undefined) window.clearTimeout(blurTimer);
  searchFocused.value = true;
  scheduleSuggestions(keywordInput.value);
}

function closeSearchPanel() {
  blurTimer = window.setTimeout(() => {
    searchFocused.value = false;
  }, 140);
}

function clearKeyword() {
  keywordInput.value = "";
  suggestions.value = [];
}

function scheduleSuggestions(value: string) {
  if (suggestionTimer !== undefined) window.clearTimeout(suggestionTimer);
  suggestionGate.invalidate();
  suggestionAbortController?.abort();
  suggestions.value = [];
  suggestionsLoading.value = false;
  const keyword = value.trim().replace(/\s+/g, " ").slice(0, 50);
  if (!searchFocused.value || !keyword) return;
  suggestionTimer = window.setTimeout(() => {
    void loadSuggestions(keyword);
  }, 250);
}

async function loadSuggestions(keyword: string) {
  const runId = suggestionGate.begin();
  suggestionAbortController?.abort();
  suggestionAbortController = new AbortController();
  suggestionsLoading.value = true;
  try {
    const data = await searchApi.suggestions(keyword, 8, suggestionAbortController.signal);
    if (suggestionGate.isCurrent(runId) && normalizedInput.value === keyword) {
      suggestions.value = data;
    }
  } catch {
    if (suggestionGate.isCurrent(runId)) suggestions.value = [];
  } finally {
    if (suggestionGate.isCurrent(runId)) suggestionsLoading.value = false;
  }
}

function selectSuggestion(suggestion: SearchSuggestion) {
  keywordInput.value = suggestion.title;
  searchFocused.value = false;
  searchHistory.value = rememberSearch(suggestion.title);
  void navigateToSearch(suggestion.title, suggestionTargetType(suggestion.type), 1);
}

function suggestionTargetType(type: SearchSuggestionType): SearchTab {
  const mapping: Record<SearchSuggestionType, SearchResultType> = {
    SONG: "songs",
    ARTIST: "artists",
    ALBUM: "albums",
    PLAYLIST: "playlists",
    USER: "users"
  };
  return mapping[type];
}

function suggestionTypeLabel(type: SearchSuggestionType) {
  const labels: Record<SearchSuggestionType, string> = {
    SONG: "歌曲",
    ARTIST: "歌手",
    ALBUM: "专辑",
    PLAYLIST: "歌单",
    USER: "用户"
  };
  return labels[type];
}

function suggestionIcon(type: SearchSuggestionType) {
  const icons: Record<SearchSuggestionType, Component> = {
    SONG: markRaw(Music2),
    ARTIST: markRaw(UserRound),
    ALBUM: markRaw(Disc3),
    PLAYLIST: markRaw(ListMusic),
    USER: markRaw(UserRound)
  };
  return icons[type];
}

function searchHistoryItem(keyword: string) {
  keywordInput.value = keyword;
  searchFocused.value = false;
  searchHistory.value = rememberSearch(keyword);
  void navigateToSearch(keyword, "all", 1);
}

function removeHistoryItem(keyword: string) {
  searchHistory.value = removeSearchHistory(keyword);
}

function clearAllHistory() {
  clearSearchHistory();
  searchHistory.value = [];
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
  player.playSong(song, displayedSongQueue.value.length ? displayedSongQueue.value : [song]);
}

function formatCount(value: number) {
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}万`;
  return new Intl.NumberFormat("zh-CN").format(value);
}
</script>
