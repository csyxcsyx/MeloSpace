<template>
  <div
    class="song-row"
    :class="{
      'song-row-active': isCurrent,
      'song-row-has-leading': Boolean($slots.leading),
      'song-row-has-custom-meta': Boolean($slots.meta),
      'song-row-has-context-actions': Boolean($slots.actions)
    }"
    @click="handleRowClick"
    @dblclick="handleRowDoubleClick"
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
          <div class="song-subtitle">
            <RouterLink
              v-if="song.artistId"
              class="song-artist song-artist-link song-artist-desktop"
              :to="`/artists/${song.artistId}`"
              :aria-label="`查看歌手 ${displayName(song.artistName, '未知歌手')}`"
              @click.stop
              @dblclick.stop
            >
              {{ displayName(song.artistName, "未知歌手") }}
            </RouterLink>
            <span v-else class="song-artist song-artist-desktop">{{ displayName(song.artistName, "未知歌手") }}</span>
            <span class="song-artist song-artist-mobile">{{ displayName(song.artistName, "未知歌手") }}</span>
            <span class="song-mobile-separator" aria-hidden="true">·</span>
            <span class="song-mobile-album">{{ displayName(song.albumTitle, "未绑定专辑") }}</span>
          </div>
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

const emit = defineEmits<{
  togglePlay: [song: Song];
  openPlayer: [song: Song];
  favoriteChange: [song: Song, favorited: boolean];
}>();

const coverLabel = computed(() => {
  if (!props.isCurrent) return `播放 ${props.song.title}`;
  return props.isPlaying ? `暂停 ${props.song.title}` : `继续播放 ${props.song.title}`;
});

const interactiveSelector = "a, button, input, label, select, textarea, [role='button'], [data-song-row-interactive]";

function isMobileSongLayout() {
  return typeof window !== "undefined" && typeof window.matchMedia === "function"
    && window.matchMedia("(max-width: 760px)").matches;
}

function handleRowClick(event: MouseEvent) {
  if (!isMobileSongLayout() || event.defaultPrevented) return;
  const target = event.target;
  if (target instanceof Element && target.closest(interactiveSelector)) return;
  emit("togglePlay", props.song);
}

function handleRowDoubleClick() {
  if (isMobileSongLayout()) return;
  emit("openPlayer", props.song);
}
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

.song-subtitle {
  display: flex;
  align-items: center;
  min-width: 0;
}

.song-mobile-separator,
.song-mobile-album,
.song-artist-mobile {
  display: none;
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
    grid-template-columns: var(--song-row-mobile-columns, minmax(0, 1fr) max-content);
    align-items: center;
    cursor: pointer;
  }

  .song-row-identity {
    grid-column: 1;
    grid-row: 1;
    gap: 8px;
  }

  .song-cover {
    position: absolute;
    width: 1px;
    height: 1px;
    min-width: 1px;
    min-height: 1px;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    clip-path: inset(50%);
    white-space: nowrap;
  }

  .song-subtitle {
    overflow: hidden;
    margin-top: 5px;
    color: var(--muted);
    font-size: 12px;
    line-height: 1.25;
    white-space: nowrap;
  }

  .song-subtitle .song-artist {
    display: inline;
    flex: 0 1 auto;
    margin-top: 0;
  }

  .song-subtitle .song-artist-desktop {
    display: none;
  }

  .song-subtitle .song-artist-mobile {
    display: inline;
  }

  .song-mobile-separator {
    display: inline;
    flex: 0 0 auto;
    margin: 0 5px;
    color: rgba(78, 82, 80, 0.48);
  }

  .song-mobile-album {
    display: inline;
    overflow: hidden;
    min-width: 0;
    color: inherit;
    text-overflow: ellipsis;
  }

  .song-row-meta {
    display: none;
  }

  .song-row-has-custom-meta .song-row-meta {
    display: flex;
    grid-column: 1;
    grid-row: 2;
    min-width: 0;
    gap: 8px;
    padding-top: 3px;
    color: var(--muted);
    font-size: 11px;
  }

  .song-row-context-actions {
    display: none;
  }

  .song-row-has-context-actions .song-row-context-actions {
    display: flex;
    grid-column: 1 / -1;
    grid-row: 3;
    justify-content: flex-start;
    padding-top: 5px;
  }

  .song-row-menu {
    grid-column: 2;
    grid-row: 1 / span 2;
    align-self: center;
  }

  .song-name-link {
    font-size: 15px;
    line-height: 1.3;
  }
}

@media (max-width: 460px) {
  .song-row.song-row {
    gap: 7px;
  }

  .song-row-meta {
    gap: 10px;
  }
}
</style>
