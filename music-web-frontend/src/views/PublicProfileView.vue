<template>
  <section class="public-profile-page" aria-labelledby="public-profile-title">
    <PageToolbar />

    <div v-if="loading" class="search-loading-state" aria-live="polite" aria-busy="true">
      <span class="search-loading-orb" aria-hidden="true" />
      <span>正在打开创作者主页…</span>
    </div>

    <div v-else-if="errorMessage" class="search-feedback-panel search-error-panel" role="alert">
      <div>
        <strong>暂时无法打开这个主页</strong>
        <p>{{ errorMessage }}</p>
      </div>
      <button class="secondary-action" type="button" @click="loadProfile">
        <RotateCcw :size="17" aria-hidden="true" />
        重试
      </button>
    </div>

    <template v-else-if="profile">
      <header class="public-profile-hero" data-glass="regular">
        <span class="public-profile-avatar">
          <img v-if="profile.avatarUrl" :src="resolveMediaUrl(profile.avatarUrl)" alt="" />
          <UserRound v-else :size="42" aria-hidden="true" />
        </span>
        <div class="public-profile-copy">
          <p class="search-eyebrow">MeloSpace 创作者</p>
          <h1 id="public-profile-title">{{ profile.nickname }}</h1>
          <p>{{ profile.bio || "这位用户还没有填写简介。" }}</p>
        </div>
        <dl class="public-profile-stats" aria-label="公开资料统计">
          <div>
            <dt><ListMusic :size="18" aria-hidden="true" />公开歌单</dt>
            <dd>{{ formatCount(profile.publicPlaylistCount) }}</dd>
          </div>
          <div>
            <dt><Heart :size="18" aria-hidden="true" />获收藏</dt>
            <dd>{{ formatCount(profile.receivedFavoriteCount) }}</dd>
          </div>
          <div>
            <dt><MessageCircle :size="18" aria-hidden="true" />评论</dt>
            <dd>{{ formatCount(profile.commentCount) }}</dd>
          </div>
        </dl>
      </header>

      <section class="public-profile-playlists" aria-labelledby="public-playlists-heading">
        <div class="section-head search-section-head">
          <div>
            <h2 id="public-playlists-heading">公开歌单</h2>
            <span>{{ formatCount(playlists.total) }} 个歌单</span>
          </div>
        </div>

        <div v-if="playlists.items.length" class="public-playlist-grid">
          <RouterLink
            v-for="playlist in playlists.items"
            :key="playlist.id"
            class="public-playlist-card"
            :to="`/playlists/${playlist.id}`"
          >
            <span class="public-playlist-cover">
              <img v-if="playlist.coverUrl" :src="resolveMediaUrl(playlist.coverUrl)" alt="" />
              <ListMusic v-else :size="30" aria-hidden="true" />
              <span class="public-playlist-play"><CirclePlay :size="28" fill="currentColor" aria-hidden="true" /></span>
            </span>
            <strong>{{ playlist.title }}</strong>
            <small>{{ playlist.songCount }} 首 · {{ formatCount(playlist.favoriteCount) }} 人收藏</small>
            <p v-if="playlist.description">{{ playlist.description }}</p>
          </RouterLink>
        </div>

        <div v-else class="search-empty-state">
          <ListMusic :size="25" aria-hidden="true" />
          <strong>还没有公开歌单</strong>
          <p>这位创作者发布歌单后，会在这里展示。</p>
        </div>

        <nav v-if="pageCount > 1" class="search-pagination" aria-label="公开歌单分页">
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
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import {
  ChevronLeft,
  ChevronRight,
  CirclePlay,
  Heart,
  ListMusic,
  MessageCircle,
  RotateCcw,
  UserRound
} from "lucide-vue-next";
import { userApi } from "@/api";
import type { PageResult, Playlist, PublicUserProfile } from "@/api/types";
import PageToolbar from "@/components/PageToolbar.vue";
import { resolveMediaUrl } from "@/utils/format";
import { createLatestRequestGate } from "@/utils/search";

const props = defineProps<{
  id: string | number;
}>();

const route = useRoute();
const profile = ref<PublicUserProfile | null>(null);
const playlists = ref<PageResult<Playlist>>({
  items: [],
  page: 1,
  size: 12,
  total: 0
});
const currentPage = ref(1);
const loading = ref(false);
const errorMessage = ref("");
const requestGate = createLatestRequestGate();
const PAGE_SIZE = 12;
const pageCount = computed(() => Math.max(1, Math.ceil(playlists.value.total / PAGE_SIZE)));

watch(
  () => [props.id, route.fullPath],
  () => {
    currentPage.value = 1;
    void loadProfile();
  },
  { immediate: true }
);

onBeforeUnmount(() => requestGate.invalidate());

async function loadProfile() {
  const userId = Number(props.id);
  const runId = requestGate.begin();
  errorMessage.value = "";
  if (!Number.isSafeInteger(userId) || userId <= 0) {
    profile.value = null;
    playlists.value = { items: [], page: 1, size: PAGE_SIZE, total: 0 };
    errorMessage.value = "用户地址无效，请从搜索结果重新进入。";
    return;
  }

  loading.value = true;
  try {
    const [profileData, playlistData] = await Promise.all([
      userApi.publicProfile(userId),
      userApi.publicPlaylists(userId, currentPage.value, PAGE_SIZE)
    ]);
    if (!requestGate.isCurrent(runId)) return;
    profile.value = profileData;
    playlists.value = playlistData;
  } catch {
    if (!requestGate.isCurrent(runId)) return;
    profile.value = null;
    playlists.value = { items: [], page: currentPage.value, size: PAGE_SIZE, total: 0 };
    errorMessage.value = "该用户可能不存在，或网络暂时不可用。";
  } finally {
    if (requestGate.isCurrent(runId)) loading.value = false;
  }
}

function setPage(page: number) {
  const nextPage = Math.min(Math.max(page, 1), pageCount.value);
  if (nextPage === currentPage.value) return;
  currentPage.value = nextPage;
  void loadProfile();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function formatCount(value: number) {
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}万`;
  return new Intl.NumberFormat("zh-CN").format(value);
}
</script>
