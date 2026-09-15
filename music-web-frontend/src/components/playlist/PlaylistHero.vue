<template>
  <section class="playlist-community-hero">
    <div class="detail-cover playlist-community-cover">
      <img v-if="playlist.coverUrl" :src="resolveMediaUrl(playlist.coverUrl)" :alt="`${playlist.title} 封面`" />
      <span v-else aria-hidden="true">♬</span>
    </div>
    <div class="detail-copy playlist-community-copy">
      <p class="feature-label">歌单 · {{ playlist.visibility === "PUBLIC" ? "公开" : "私有" }}</p>
      <h1 class="page-title">{{ playlist.title }}</h1>
      <p class="playlist-description">{{ playlist.description || "这个歌单还没有描述。" }}</p>
      <RouterLink class="playlist-creator" :to="`/users/${playlist.userId}`">
        <span class="playlist-creator-avatar">
          <img v-if="playlist.creatorAvatarUrl" :src="resolveMediaUrl(playlist.creatorAvatarUrl)" alt="" />
          <span v-else>{{ creatorInitial }}</span>
        </span>
        <strong>{{ playlist.creatorNickname || "MeloSpace 用户" }}</strong>
      </RouterLink>
      <div v-if="playlist.tags.length" class="playlist-tags" aria-label="歌单标签">
        <span v-for="tag in playlist.tags" :key="tag"># {{ tag }}</span>
      </div>
      <p class="playlist-meta-line">
        <span>{{ playlist.songCount }} 首</span>
        <span>{{ formatCount(playlist.playCount) }} 次播放</span>
        <span>{{ formatCount(playlist.favoriteCount) }} 人收藏</span>
        <span>{{ formatCount(playlist.commentCount) }} 条评论</span>
        <span>更新于 {{ formatDate(playlist.updatedAt) }}</span>
      </p>
      <div class="detail-actions playlist-primary-actions">
        <button type="button" class="primary-action" :disabled="!hasSongs" @click="$emit('play-all')">
          <Play :size="18" fill="currentColor" />
          播放全部
        </button>
        <button type="button" class="secondary-action" :disabled="!hasSongs" @click="$emit('shuffle')">
          <Shuffle :size="18" />
          随机播放
        </button>
        <button
          v-if="!playlist.canManage"
          type="button"
          class="secondary-action"
          :class="{ 'is-active': playlist.favorited }"
          :disabled="favoriteSaving"
          :aria-pressed="playlist.favorited"
          @click="$emit('favorite')"
        >
          <Heart :size="18" :fill="playlist.favorited ? 'currentColor' : 'none'" />
          {{ playlist.favorited ? "已收藏" : "收藏" }}
        </button>
        <button type="button" class="secondary-action" @click="$emit('share')">
          <Share2 :size="18" />
          分享
        </button>
        <button
          v-if="playlist.canManage"
          type="button"
          class="secondary-action"
          :aria-expanded="editing"
          @click="$emit('edit')"
        >
          <Pencil :size="18" />
          编辑歌单
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Heart, Pencil, Play, Share2, Shuffle } from "lucide-vue-next";
import type { PlaylistDetail } from "@/api/types";
import { formatCount, resolveMediaUrl } from "@/utils/format";

const props = defineProps<{
  playlist: PlaylistDetail;
  hasSongs: boolean;
  favoriteSaving: boolean;
  editing: boolean;
}>();

defineEmits<{
  "play-all": [];
  shuffle: [];
  favorite: [];
  share: [];
  edit: [];
}>();

const creatorInitial = computed(() => (props.playlist.creatorNickname || "M").trim().slice(0, 1).toUpperCase());

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "最近"
    : new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "short", day: "numeric" }).format(date);
}
</script>

<style scoped>
.playlist-community-hero {
  display: grid;
  align-items: end;
  gap: clamp(22px, 4vw, 44px);
  grid-template-columns: minmax(190px, 270px) minmax(0, 1fr);
}

.playlist-community-cover {
  max-width: 270px;
  justify-self: start;
}

.playlist-community-copy { min-width: 0; }
.playlist-community-copy .page-title { overflow-wrap: anywhere; }

.playlist-description {
  max-width: 720px;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.playlist-creator {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  gap: 9px;
  margin: 5px 0;
  color: inherit;
  text-decoration: none;
}

.playlist-creator:hover strong,
.playlist-creator:focus-visible strong { color: var(--brand); }

.playlist-creator-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: linear-gradient(145deg, var(--brand-soft), #fff);
  color: var(--brand);
  font-size: 13px;
}

.playlist-creator-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.playlist-tags,
.playlist-meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.playlist-tags { margin: 8px 0; }
.playlist-tags span {
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
  font-weight: 680;
}

.playlist-meta-line { font-size: 13px; }
.playlist-meta-line span:not(:last-child)::after {
  margin-left: 12px;
  content: "·";
  color: #b5b5ba;
}

.playlist-primary-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  gap: 7px;
}

.playlist-primary-actions .is-active {
  border-color: color-mix(in srgb, var(--brand) 34%, transparent);
  background: var(--brand-soft);
  color: var(--brand);
}

@media (max-width: 820px) {
  .playlist-community-hero {
    align-items: center;
    grid-template-columns: minmax(130px, 190px) minmax(0, 1fr);
  }
}

@media (max-width: 620px) {
  .playlist-community-hero {
    align-items: start;
    grid-template-columns: minmax(104px, 34vw) minmax(0, 1fr);
  }

  .playlist-community-cover {
    max-width: 150px;
    border-radius: 18px;
  }

  .playlist-community-copy .page-title {
    margin-bottom: 8px;
    font-size: clamp(26px, 8vw, 36px);
  }

  .playlist-description,
  .playlist-tags,
  .playlist-meta-line,
  .playlist-primary-actions { grid-column: 1 / -1; }
  .playlist-community-copy { display: contents; }
  .playlist-community-copy > .feature-label,
  .playlist-community-copy > .page-title,
  .playlist-community-copy > .playlist-creator { grid-column: 2; }
  .playlist-community-copy > .feature-label { align-self: end; margin: 4px 0 0; }
  .playlist-community-copy > .page-title { align-self: center; }
  .playlist-community-copy > .playlist-creator { align-self: start; }
  .playlist-description { margin-top: 2px; }
  .playlist-primary-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .playlist-primary-actions button { width: 100%; }
}

@media (max-width: 390px) {
  .playlist-community-hero { gap: 16px 14px; }
  .playlist-meta-line { gap: 5px 8px; }
  .playlist-meta-line span:not(:last-child)::after { margin-left: 8px; }
  .playlist-primary-actions { grid-template-columns: minmax(0, 1fr); }
}
</style>
