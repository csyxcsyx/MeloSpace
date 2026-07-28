<template>
  <div
    class="song-row"
    :class="{ 'song-row-active': isCurrent }"
    @dblclick="$emit('openPlayer', song)"
  >
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
      <RouterLink
        class="song-name song-name-link"
        :to="{ name: 'song-detail', params: { id: song.id } }"
        :aria-label="`查看歌曲 ${song.title} 的详情与评论`"
        @click.stop
        @dblclick.stop
      >
        {{ song.title }}
      </RouterLink>
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
    </div>
    <SongActionsMenu :song="song" />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";
import { Music, Pause, Play } from "lucide-vue-next";
import SongActionsMenu from "@/components/SongActionsMenu.vue";
import type { Song } from "@/api/types";
import { displayName, resolveMediaUrl } from "@/utils/format";

const props = defineProps<{
  song: Song;
  isCurrent?: boolean;
  isPlaying?: boolean;
}>();

defineEmits<{
  togglePlay: [song: Song];
  openPlayer: [song: Song];
  more: [song: Song];
}>();

const coverLabel = computed(() => {
  if (!props.isCurrent) return `播放 ${props.song.title}`;
  return props.isPlaying ? `暂停 ${props.song.title}` : `继续播放 ${props.song.title}`;
});
</script>

<style scoped>
.song-row.song-row {
  grid-template-columns: max-content minmax(0, 1fr) 44px;
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
</style>
