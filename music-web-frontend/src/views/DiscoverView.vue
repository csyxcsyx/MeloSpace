<template>
  <section class="discover-page community-discover-page">
    <PageHeader
      class="discover-title-row"
      data-glass="regular"
      eyebrow="MeloSpace 每日推荐"
      title="一首一首，遇见今天的好音乐"
      description="从曲库中为你挑选 12 首歌，轻一点封面就能开始播放。"
    >
      <template #actions>
        <RouterLink class="secondary-action discover-search-link" to="/search">搜索全站</RouterLink>
      </template>
    </PageHeader>

    <EmptyState v-if="discover.loading && !discover.loaded">正在准备今天的歌曲...</EmptyState>
    <section v-else ref="recommendationRef" class="discover-recommendations community-section">
      <div class="section-head recommendation-head">
        <div>
          <p class="feature-label">今日 12 首</p>
          <h2>为你逐首推荐</h2>
        </div>
        <button class="secondary-action refresh-recommendation" type="button" @click="refreshRecommendations">
          <RefreshCw :size="16" />
          换一批
        </button>
      </div>

      <Transition name="recommendation-swap" mode="out-in">
        <SongList
          v-if="recommendedSongs.length"
          :key="recommendationKey"
          class="discover-song-list"
          :songs="recommendedSongs"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        >
          <template #leading="{ index }">
            <span class="recommendation-number">{{ String(index + 1).padStart(2, "0") }}</span>
          </template>
        </SongList>
        <EmptyState v-else key="empty">还没有可展示的歌曲。</EmptyState>
      </Transition>
    </section>

    <section v-if="communityLoading || community?.hotComments.length" class="community-section hot-comment-section">
      <div class="section-head">
        <div>
          <p class="feature-label">近期热评</p>
          <h2>听友们正在聊</h2>
        </div>
      </div>
      <EmptyState v-if="communityLoading">正在加载社区热评...</EmptyState>
      <div v-else class="discover-comment-grid">
        <RouterLink
          v-for="comment in community?.hotComments || []"
          :key="comment.id"
          class="discover-comment-card"
          data-glass="solid"
          :to="targetPath(comment)"
        >
          <div class="discover-comment-author">
            <span>{{ (comment.userNickname || "M").slice(0, 1) }}</span>
            <strong>{{ comment.userNickname || "MeloSpace 用户" }}</strong>
            <small>{{ relativeTime(comment.createdAt) }}</small>
          </div>
          <p>{{ comment.content }}</p>
          <div class="discover-comment-target">
            <span class="comment-target-cover">
              <img
                v-if="comment.targetCoverUrl"
                :src="resolveMediaUrl(comment.targetCoverUrl)"
                alt=""
                loading="lazy"
                decoding="async"
              />
              <span v-else>♪</span>
            </span>
            <span>来自《{{ comment.targetTitle }}》</span>
            <small>♡ {{ comment.likeCount }} · 回复 {{ comment.replyCount }}</small>
          </div>
        </RouterLink>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { RefreshCw } from "lucide-vue-next";
import { discoverApi } from "@/api";
import type { CommunityDiscover, DiscoverComment, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageHeader from "@/components/PageHeader.vue";
import SongList from "@/components/SongList.vue";
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
  gap: 38px;
  min-width: 0;
  padding-bottom: 28px;
}

.discover-title-row {
  align-items: end;
}

.discover-title-row :deep(.page-header-copy) {
  max-width: 780px;
}

.discover-search-link {
  flex: 0 0 auto;
}

.community-section {
  min-width: 0;
  scroll-margin-top: 18px;
}

.recommendation-head {
  justify-content: space-between;
  gap: 14px;
}

.recommendation-number {
  display: block;
  width: 24px;
  color: #a1a1a8;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 760;
  text-align: center;
}

.discover-song-list {
  border-radius: 14px;
}

.discover-song-list :deep(.song-list-header-identity) {
  padding-left: 96px;
}

.hot-comment-section {
  padding-top: 4px;
}

.discover-comment-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.discover-comment-card {
  display: grid;
  min-width: 0;
  gap: 13px;
  border: 1px solid rgba(49, 79, 68, 0.09);
  border-radius: 20px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--lg-inner-light), 0 12px 32px rgba(42, 76, 64, 0.055);
  color: inherit;
  text-decoration: none;
  content-visibility: auto;
  contain-intrinsic-size: auto 190px;
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
  margin: 0;
  overflow: hidden;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.discover-comment-target {
  display: grid;
  align-items: center;
  min-width: 0;
  gap: 10px;
  grid-template-columns: 42px minmax(0, 1fr) max-content;
}

.comment-target-cover {
  display: grid;
  width: 42px;
  height: 42px;
  overflow: hidden;
  border-radius: 10px;
  background: #edf3f0;
  place-items: center;
}

.comment-target-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 620px) {
  .community-discover-page {
    gap: 30px;
  }

  .discover-title-row :deep(.page-title) {
    font-size: clamp(29px, 9vw, 36px);
    line-height: 1.08;
  }

  .recommendation-number {
    width: 18px;
    font-size: 10px;
  }

  .discover-comment-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .discover-comment-author {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .discover-comment-author small {
    grid-column: 2;
  }

  .discover-comment-target {
    grid-template-columns: 42px minmax(0, 1fr);
  }

  .discover-comment-target small {
    grid-column: 2;
  }
}

</style>
