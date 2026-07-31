<template>
  <section class="discover-page community-discover-page">
    <header class="page-header discover-title-row">
      <div>
        <p class="feature-label">MeloSpace 每日推荐</p>
        <h1 class="page-title">一首一首，遇见今天的好音乐</h1>
        <p class="discover-subtitle">从曲库中为你挑选 12 首歌，轻一点封面就能开始播放。</p>
      </div>
      <RouterLink class="secondary-action discover-search-link" to="/search">搜索全站</RouterLink>
    </header>

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
        <div v-if="recommendedSongs.length" :key="recommendationKey" class="song-recommendation-feed">
          <article
            v-for="(song, index) in recommendedSongs"
            :key="song.id"
            class="song-recommendation-card"
            :class="{ 'is-current': player.currentSong?.id === song.id }"
          >
            <span class="recommendation-number">{{ String(index + 1).padStart(2, "0") }}</span>
            <button
              class="recommendation-cover-button"
              type="button"
              :aria-label="player.currentSong?.id === song.id && player.isPlaying ? `暂停 ${song.title}` : `播放 ${song.title}`"
              @click="toggleSongPlayback(song)"
            >
              <img
                v-if="song.coverUrl"
                :src="resolveMediaUrl(song.coverUrl)"
                :alt="`${song.title} 封面`"
                :loading="index < 2 ? 'eager' : 'lazy'"
                decoding="async"
              />
              <span v-else class="recommendation-cover-fallback">♪</span>
              <span class="recommendation-play-icon">
                <Pause v-if="player.currentSong?.id === song.id && player.isPlaying" :size="21" fill="currentColor" />
                <Play v-else :size="21" fill="currentColor" />
              </span>
            </button>

            <div class="recommendation-copy">
              <div class="recommendation-heading">
                <div>
                  <RouterLink class="recommendation-title" :to="`/songs/${song.id}`">{{ song.title }}</RouterLink>
                  <p>{{ song.artistName || "未知歌手" }}<span v-if="song.albumTitle"> · {{ song.albumTitle }}</span></p>
                </div>
                <span v-if="player.currentSong?.id === song.id" class="now-playing-label">
                  {{ player.isPlaying ? "正在播放" : "已暂停" }}
                </span>
              </div>

              <div class="recommendation-tags">
                <span v-if="song.genre">{{ song.genre }}</span>
                <span v-if="song.mood">{{ song.mood }}</span>
                <span v-if="song.language">{{ song.language }}</span>
              </div>

              <div class="recommendation-meta">
                <span><Headphones :size="15" /> {{ formatCount(song.playCount) }} 次播放</span>
                <span><Clock3 :size="15" /> {{ formatDuration(song.durationSeconds) }}</span>
              </div>
            </div>

            <div class="recommendation-actions">
              <button class="primary-action recommendation-listen" type="button" @click="openPlayer(song)">
                <AudioLines :size="17" />
                沉浸播放
              </button>
              <RouterLink class="recommendation-detail-link" :to="`/songs/${song.id}`">
                详情与评论 <ChevronRight :size="17" />
              </RouterLink>
            </div>
          </article>
        </div>
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
import { AudioLines, ChevronRight, Clock3, Headphones, Pause, Play, RefreshCw } from "lucide-vue-next";
import { discoverApi } from "@/api";
import type { CommunityDiscover, DiscoverComment, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import { useDiscoverStore } from "@/stores/discover";
import { usePlayerStore } from "@/stores/player";
import { formatDuration, resolveMediaUrl } from "@/utils/format";

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

function formatCount(value: number) {
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)} 万`;
  return new Intl.NumberFormat("zh-CN").format(value);
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
  display: flex;
  align-items: end;
  justify-content: space-between;
  min-width: 0;
  gap: 20px;
}

.discover-title-row > div {
  min-width: 0;
}

.discover-title-row .page-title {
  max-width: 780px;
  overflow-wrap: anywhere;
}

.discover-subtitle {
  max-width: 680px;
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.65;
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

.song-recommendation-feed {
  display: grid;
  gap: 12px;
}

.song-recommendation-card {
  position: relative;
  display: grid;
  align-items: center;
  min-width: 0;
  min-height: 128px;
  gap: 16px;
  border: 1px solid rgba(20, 20, 24, 0.07);
  border-radius: 22px;
  padding: 14px 16px 14px 13px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 9px 28px rgba(15, 15, 20, 0.055);
  grid-template-columns: 34px 100px minmax(0, 1fr) max-content;
  content-visibility: auto;
  contain-intrinsic-size: auto 128px;
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.song-recommendation-card:hover {
  border-color: rgba(235, 40, 76, 0.2);
  box-shadow: 0 14px 34px rgba(15, 15, 20, 0.085);
  transform: translateY(-1px);
}

.song-recommendation-card.is-current {
  border-color: rgba(235, 40, 76, 0.28);
  background: linear-gradient(105deg, rgba(255, 247, 249, 0.97), rgba(255, 255, 255, 0.94));
}

.recommendation-number {
  color: #a1a1a8;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  font-weight: 760;
  text-align: center;
}

.recommendation-cover-button {
  position: relative;
  display: grid;
  width: 100px;
  height: 100px;
  overflow: hidden;
  border: 0;
  border-radius: 17px;
  padding: 0;
  background: linear-gradient(145deg, #f7e7ea, #e8e8ee);
  color: white;
  cursor: pointer;
  place-items: center;
}

.recommendation-cover-button img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 240ms ease;
}

.recommendation-cover-button:hover img {
  transform: scale(1.035);
}

.recommendation-cover-fallback {
  color: var(--brand);
  font-size: 28px;
}

.recommendation-play-icon {
  position: absolute;
  inset: 0;
  display: grid;
  background: rgba(12, 12, 15, 0.22);
  opacity: 0;
  transition: opacity 160ms ease;
  place-items: center;
}

.recommendation-cover-button:hover .recommendation-play-icon,
.recommendation-cover-button:focus-visible .recommendation-play-icon,
.is-current .recommendation-play-icon {
  opacity: 1;
}

.recommendation-copy {
  display: grid;
  min-width: 0;
  gap: 10px;
}

.recommendation-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
  gap: 12px;
}

.recommendation-heading > div {
  min-width: 0;
}

.recommendation-title {
  display: block;
  overflow: hidden;
  color: var(--text);
  font-size: clamp(18px, 1.55vw, 22px);
  font-weight: 820;
  line-height: 1.25;
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommendation-title:hover {
  color: var(--brand);
}

.recommendation-heading p {
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.now-playing-label {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 9px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
  font-weight: 780;
}

.recommendation-tags,
.recommendation-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.recommendation-tags span {
  border-radius: 999px;
  padding: 4px 9px;
  background: #f2f2f5;
  color: #696970;
  font-size: 12px;
}

.recommendation-meta {
  color: var(--muted);
  font-size: 13px;
  gap: 16px;
}

.recommendation-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.recommendation-actions {
  display: grid;
  justify-items: stretch;
  width: 132px;
  gap: 8px;
}

.recommendation-listen {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  gap: 7px;
  white-space: nowrap;
}

.recommendation-detail-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.recommendation-detail-link:hover {
  color: var(--brand);
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
  border: 1px solid rgba(20, 20, 24, 0.06);
  border-radius: 20px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.86);
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
  background: #ececf1;
  place-items: center;
}

.comment-target-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 900px) {
  .song-recommendation-card {
    grid-template-columns: 28px 88px minmax(0, 1fr);
  }

  .recommendation-cover-button {
    width: 88px;
    height: 88px;
  }

  .recommendation-actions {
    display: flex;
    width: auto;
    grid-column: 3;
  }

  .recommendation-listen,
  .recommendation-detail-link {
    min-width: 128px;
  }
}

@media (max-width: 620px) {
  .community-discover-page {
    gap: 30px;
  }

  .discover-title-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .discover-title-row .page-title {
    font-size: clamp(29px, 9vw, 36px);
    line-height: 1.08;
  }

  .discover-search-link {
    display: none;
  }

  .song-recommendation-card {
    min-height: 106px;
    gap: 12px;
    border-radius: 18px;
    padding: 11px;
    grid-template-columns: 78px minmax(0, 1fr);
    contain-intrinsic-size: auto 106px;
  }

  .recommendation-number {
    position: absolute;
    top: 15px;
    left: 15px;
    z-index: 1;
    border-radius: 999px;
    padding: 3px 6px;
    background: rgba(10, 10, 12, 0.56);
    color: white;
    font-size: 10px;
  }

  .recommendation-cover-button {
    width: 78px;
    height: 78px;
    border-radius: 14px;
  }

  .recommendation-title {
    font-size: 17px;
  }

  .recommendation-heading p {
    font-size: 13px;
  }

  .recommendation-tags {
    display: none;
  }

  .recommendation-meta {
    gap: 10px;
    font-size: 12px;
  }

  .recommendation-meta span:first-child {
    display: none;
  }

  .now-playing-label {
    display: none;
  }

  .recommendation-actions {
    display: none;
  }

  .recommendation-play-icon {
    opacity: 1;
    background: linear-gradient(0deg, rgba(10, 10, 12, 0.32), transparent 58%);
    align-items: end;
    justify-content: end;
    padding: 0 8px 7px 0;
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

@media (prefers-reduced-motion: reduce) {
  .song-recommendation-card,
  .recommendation-cover-button img {
    transition: none;
  }
}
</style>
