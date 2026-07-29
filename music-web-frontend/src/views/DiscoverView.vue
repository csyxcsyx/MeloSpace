<template>
  <section class="discover-page community-discover-page">
    <header class="page-header discover-title-row">
      <div>
        <p class="feature-label">MeloSpace 社区</p>
        <h1 class="page-title">发现好音乐，也发现同路的人</h1>
      </div>
      <RouterLink class="secondary-action" to="/search">搜索全站</RouterLink>
    </header>

    <button class="discover-banner" type="button" @click="scrollToRecommendations">
      <img src="/discover-banner.png" alt="MeloSpace 发现属于你的音乐空间" />
    </button>

    <EmptyState v-if="discover.loading && !discover.loaded">正在加载音乐内容...</EmptyState>
    <section v-else ref="recommendationRef" class="discover-recommendations community-section">
      <div class="section-head recommendation-head">
        <div>
          <p class="feature-label">每日歌曲</p>
          <h2>今天听点不一样的</h2>
        </div>
        <button class="secondary-action refresh-recommendation" type="button" @click="refreshRecommendations">
          <RefreshCw :size="16" />
          换一批
        </button>
      </div>
      <Transition name="recommendation-swap" mode="out-in">
        <SongColumnList
          v-if="recommendedSongs.length"
          :key="recommendationKey"
          :songs="recommendedSongs"
          :column-count="3"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else key="empty">还没有可展示的歌曲。</EmptyState>
      </Transition>
    </section>

    <EmptyState v-if="communityLoading">正在加载社区精选...</EmptyState>
    <template v-else-if="community">
      <section class="community-section">
        <div class="section-head">
          <div>
            <p class="feature-label">社区热门</p>
            <h2>正在被大家收藏的歌单</h2>
          </div>
        </div>
        <div v-if="community.popularPlaylists.length" class="community-playlist-grid">
          <RouterLink
            v-for="playlist in community.popularPlaylists"
            :key="playlist.id"
            class="community-playlist-card"
            :to="`/playlists/${playlist.id}`"
          >
            <span class="community-card-cover">
              <img v-if="playlist.coverUrl" :src="resolveMediaUrl(playlist.coverUrl)" alt="" />
              <span v-else>♬</span>
            </span>
            <strong>{{ playlist.title }}</strong>
            <span>{{ playlist.creatorNickname || "MeloSpace 用户" }}</span>
            <small>
              <Heart :size="14" /> {{ playlist.favoriteCount }}
              <MessageCircle :size="14" /> {{ playlist.commentCount }}
              <Play :size="14" /> {{ playlist.playCount }}
            </small>
          </RouterLink>
        </div>
        <EmptyState v-else>社区还没有公开歌单。</EmptyState>
      </section>

      <section class="community-section">
        <div class="section-head">
          <div>
            <p class="feature-label">新鲜出炉</p>
            <h2>最近更新的歌单</h2>
          </div>
        </div>
        <div class="latest-playlist-list">
          <RouterLink
            v-for="playlist in community.latestPlaylists"
            :key="playlist.id"
            :to="`/playlists/${playlist.id}`"
          >
            <span class="latest-cover">
              <img v-if="playlist.coverUrl" :src="resolveMediaUrl(playlist.coverUrl)" alt="" />
              <span v-else>♪</span>
            </span>
            <span>
              <strong>{{ playlist.title }}</strong>
              <small>{{ playlist.songCount }} 首 · {{ formatDate(playlist.updatedAt) }}</small>
            </span>
            <span class="latest-chevron">›</span>
          </RouterLink>
        </div>
      </section>

      <section class="community-section">
        <div class="section-head">
          <div>
            <p class="feature-label">近期热评</p>
            <h2>听友们正在聊</h2>
          </div>
        </div>
        <div v-if="community.hotComments.length" class="discover-comment-grid">
          <RouterLink
            v-for="comment in community.hotComments"
            :key="comment.id"
            class="discover-comment-card"
            :to="targetPath(comment)"
          >
            <div class="discover-comment-author">
              <span>{{ (comment.userNickname || "M").slice(0, 1) }}</span>
              <strong>{{ comment.userNickname }}</strong>
              <small>{{ relativeTime(comment.createdAt) }}</small>
            </div>
            <p>{{ comment.content }}</p>
            <div class="discover-comment-target">
              <span class="latest-cover">
                <img v-if="comment.targetCoverUrl" :src="resolveMediaUrl(comment.targetCoverUrl)" alt="" />
                <span v-else>♪</span>
              </span>
              <span>来自《{{ comment.targetTitle }}》</span>
              <small>♡ {{ comment.likeCount }} · 回复 {{ comment.replyCount }}</small>
            </div>
          </RouterLink>
        </div>
        <EmptyState v-else>还没有近期热评，去歌曲详情留下第一条感受吧。</EmptyState>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Heart, MessageCircle, Play, RefreshCw } from "lucide-vue-next";
import { discoverApi } from "@/api";
import type { CommunityDiscover, DiscoverComment, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { useDiscoverStore } from "@/stores/discover";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

defineOptions({ name: "DiscoverView" });

const player = usePlayerStore();
const discover = useDiscoverStore();
const router = useRouter();
const recommendationRef = ref<HTMLElement | null>(null);
const community = ref<CommunityDiscover | null>(null);
const communityLoading = ref(true);
const recommendedSongs = computed(() => discover.recommendedSongs);
const recommendationKey = computed(() => recommendedSongs.value.map((song) => song.id).join("-"));

onMounted(async () => {
  await Promise.all([discover.load(), loadCommunity()]);
});

async function loadCommunity() {
  communityLoading.value = true;
  try {
    community.value = await discoverApi.community();
  } finally {
    communityLoading.value = false;
  }
}

function refreshRecommendations() {
  discover.refreshRecommendations();
}

function scrollToRecommendations() {
  recommendationRef.value?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function toggleSongPlayback(song: Song) {
  if (player.currentSong?.id === song.id) {
    player.isPlaying ? player.setPlaying(false) : void player.resumeCurrent();
    return;
  }
  void player.playSong(song, recommendedSongs.value);
}

async function openPlayer(song: Song) {
  const played = await player.playSong(song, recommendedSongs.value);
  if (played) await router.push("/player");
}

function targetPath(comment: DiscoverComment) {
  return comment.targetType === "PLAYLIST"
    ? `/playlists/${comment.targetId}`
    : `/songs/${comment.targetId}`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("zh-CN", { month: "short", day: "numeric" }).format(new Date(value));
}

function relativeTime(value: string) {
  const minutes = Math.max(0, Math.floor((Date.now() - Date.parse(value)) / 60000));
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)} 小时前`;
  return `${Math.floor(minutes / 1440)} 天前`;
}
</script>

<style scoped>
.community-discover-page {
  display: grid;
  gap: 36px;
  min-width: 0;
  padding-bottom: 28px;
}

.discover-title-row {
  display: flex;
  align-items: end;
  justify-content: space-between;
  min-width: 0;
  gap: 16px;
}

.discover-title-row > div {
  min-width: 0;
}

.discover-title-row .page-title {
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.discover-title-row p,
.discover-title-row h1,
.community-section h2,
.community-section p {
  margin-top: 0;
}

.community-section {
  min-width: 0;
  scroll-margin-top: 18px;
}

.community-section :deep(.song-columns) {
  max-height: none;
  overflow: visible;
}

.community-playlist-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.community-playlist-card {
  display: grid;
  min-width: 0;
  gap: 7px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 20px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.62);
  color: inherit;
  text-decoration: none;
  box-shadow: 0 14px 38px rgba(15, 15, 20, 0.07);
  backdrop-filter: blur(20px) saturate(140%);
}

.community-card-cover {
  display: grid;
  width: 100%;
  aspect-ratio: 1;
  place-items: center;
  overflow: hidden;
  border-radius: 14px;
  background: linear-gradient(145deg, #f6e2e6, #e7e7ed);
  color: var(--brand);
  font-size: 30px;
}

.community-card-cover img,
.latest-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.community-playlist-card > strong,
.community-playlist-card > span:not(.community-card-cover) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.community-playlist-card > span {
  color: var(--muted);
  font-size: 13px;
}

.community-playlist-card small {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--muted);
}

.latest-playlist-list {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.latest-playlist-list > a {
  display: grid;
  align-items: center;
  min-height: 68px;
  gap: 12px;
  border-radius: 16px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.54);
  color: inherit;
  text-decoration: none;
  grid-template-columns: 52px minmax(0, 1fr) 24px;
}

.latest-cover {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  overflow: hidden;
  border-radius: 11px;
  background: #ececf1;
}

.latest-playlist-list strong,
.latest-playlist-list small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.latest-playlist-list small,
.latest-chevron {
  color: var(--muted);
}

.discover-comment-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.discover-comment-card {
  display: grid;
  gap: 13px;
  border-radius: 20px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.62);
  color: inherit;
  text-decoration: none;
}

.discover-comment-author {
  display: grid;
  align-items: center;
  gap: 9px;
  grid-template-columns: 34px minmax(0, 1fr) max-content;
}

.discover-comment-author > span {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand);
}

.discover-comment-author small,
.discover-comment-target small {
  color: var(--muted);
}

.discover-comment-card > p {
  display: -webkit-box;
  margin-bottom: 0;
  overflow: hidden;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.discover-comment-target {
  display: grid;
  align-items: center;
  gap: 10px;
  grid-template-columns: 42px minmax(0, 1fr) max-content;
}

.discover-comment-target .latest-cover {
  width: 42px;
  height: 42px;
}

@media (max-width: 900px) {
  .community-playlist-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .community-discover-page {
    gap: 28px;
  }

  .discover-title-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .discover-title-row .page-title {
    font-size: clamp(29px, 9vw, 36px);
    line-height: 1.08;
  }

  .latest-playlist-list,
  .discover-comment-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .discover-comment-author {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .discover-comment-author small {
    grid-column: 2;
  }
}
</style>
