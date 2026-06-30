<template>
  <div
    class="song-row"
    :class="{ 'song-row-active': isCurrent }"
    tabindex="0"
    :aria-label="`双击播放 ${song.title} 并打开歌词`"
    @dblclick="$emit('openPlayer', song)"
    @keydown.enter="$emit('openPlayer', song)"
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
      <div class="song-name">{{ song.title }}</div>
      <RouterLink
        v-if="song.artistId"
        class="song-artist"
        :to="`/artists/${song.artistId}`"
        @click.stop
        @dblclick.stop
      >
        {{ displayName(song.artistName, "未知歌手") }}
      </RouterLink>
      <div v-else class="song-artist">{{ displayName(song.artistName, "未知歌手") }}</div>
    </div>
    <button class="more" type="button" aria-label="更多操作" @click.stop="$emit('more', song)" @dblclick.stop>
      <MoreHorizontal :size="18" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { MoreHorizontal, Music, Pause, Play } from "lucide-vue-next";
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
