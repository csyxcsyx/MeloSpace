<template>
  <div
    class="song-row"
    :class="{ 'song-row-active': isCurrent }"
    @dblclick="$emit('openPlayer', song)"
  >
    <div class="song-row-identity">
      <div v-if="$slots.leading" class="song-row-leading">
        <slot name="leading" :song="song" />
      </div>
      <button
        class="song-cover"
        :class="{ 'song-cover-playing': isCurrent && isPlaying }"
        type="button"
        :aria-label="coverLabel"
        :title="coverLabel"
        @click.stop="$emit('togglePlay', song)"
        @dblclick.stop
      >
        <img v-if="song.coverUrl" :src="resolveMediaUrl(song.coverUrl)" alt="" />
        <Music v-else :size="16" />
        <span class="song-cover-action" aria-hidden="true">
          <Pause v-if="isCurrent && isPlaying" :size="18" fill="currentColor" />
          <Play v-else :size="18" fill="currentColor" />
        </span>
      </button>
      <div class="song-info">
        <slot name="title" :song="song">
          <RouterLink
            class="song-name song-name-link"
            :to="{ name: 'song-detail', params: { id: song.id } }"
            :aria-label="`查看歌曲 ${song.title} 的详情与评论`"
            @click.stop
            @dblclick.stop
          >
            {{ song.title }}
          </RouterLink>
        </slot>
        <slot name="subtitle" :song="song">
          <RouterLink
            v-if="song.artistId"
            class="song-artist song-artist-link"
            :to="`/artists/${song.artistId}`"
            :aria-label="`查看歌手 ${displayName(song.artistName, '未知歌手')}`"
            @click.stop
            @dblclick.stop
          >
            {{ displayName(song.artistName, "未知歌手") }}
          </RouterLink>
          <div v-else class="song-artist">{{ displayName(song.artistName, "未知歌手") }}</div>
        </slot>
      </div>
    </div>
    <div class="song-row-meta">
      <slot name="meta" :song="song">
        <RouterLink
          v-if="song.albumId"
          class="song-row-album"
          :to="`/albums/${song.albumId}`"
          @click.stop
          @dblclick.stop
        >
          {{ displayName(song.albumTitle, "未绑定专辑") }}
        </RouterLink>
        <span v-else class="song-row-album">未绑定专辑</span>
        <span class="song-row-duration">{{ formatDuration(song.durationSeconds) }}</span>
      </slot>
    </div>
    <div class="song-row-context-actions">
      <slot name="actions" :song="song" />
    </div>
    <SongActionsMenu
      class="song-row-menu"
      :song="song"
      :favorited="favorited"
      @favorite-change="$emit('favoriteChange', song, $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";
import { Music, Pause, Play } from "lucide-vue-next";
import SongActionsMenu from "@/components/SongActionsMenu.vue";
import type { Song } from "@/api/types";
import { displayName, formatDuration, resolveMediaUrl } from "@/utils/format";

const props = defineProps<{
  song: Song;
  isCurrent?: boolean;
  isPlaying?: boolean;
  favorited?: boolean;
}>();

defineEmits<{
  togglePlay: [song: Song];
  openPlayer: [song: Song];
  favoriteChange: [song: Song, favorited: boolean];
}>();

const coverLabel = computed(() => {
  if (!props.isCurrent) return `播放 ${props.song.title}`;
  return props.isPlaying ? `暂停 ${props.song.title}` : `继续播放 ${props.song.title}`;
});
</script>

<style scoped>
.song-row.song-row {
  grid-template-columns: var(--song-row-columns, minmax(250px, 1.25fr) minmax(220px, 0.85fr) max-content 168px);
}

.song-row-identity,
.song-row-leading,
.song-row-context-actions {
  display: flex;
  align-items: center;
  min-width: 0;
}

.song-row-identity {
  gap: 12px;
}

.song-row-leading {
  flex: 0 0 auto;
  justify-content: center;
}

.song-info {
  flex: 1 1 auto;
}

.song-row-meta {
  display: grid;
  align-items: center;
  min-width: 0;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr) max-content;
  color: var(--muted);
  font-size: 12px;
}

.song-row-meta :deep(*) {
  overflow: hidden;
  min-width: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.song-row-album {
  color: inherit;
}

.song-row-album:hover,
.song-row-album:focus-visible {
  color: var(--brand);
  outline: 0;
}

.song-name-link {
  display: block;
  width: max-content;
  max-width: 100%;
  color: #303035;
  text-decoration: none;
  text-underline-offset: 3px;
}

.song-name-link:hover,
.song-name-link:focus-visible {
  color: var(--brand);
  outline: 0;
  text-decoration: underline;
}

@media (max-width: 760px) {
  .song-row.song-row {
    grid-template-columns: var(--song-row-mobile-columns, minmax(0, 1fr) 44px);
  }

  .song-row-meta {
    grid-column: 1;
    grid-row: 2;
    padding-left: 60px;
  }

  .song-row-context-actions {
    grid-column: 1;
    grid-row: 3;
    padding-left: 60px;
  }

  .song-row-menu {
    grid-column: 2;
    grid-row: 1 / span 3;
  }
}

@media (max-width: 460px) {
  .song-row.song-row {
    gap: 9px;
    padding-right: 2px;
  }

  .song-row-meta {
    gap: 10px;
  }
}
</style>
