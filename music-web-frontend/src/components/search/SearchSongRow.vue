<template>
  <article class="search-song-row">
    <button
      type="button"
      class="search-song-play"
      :aria-label="isCurrent && isPlaying ? `暂停 ${song.title}` : `播放 ${song.title}`"
      @click="$emit('toggle', song)"
    >
      <img v-if="song.coverUrl" :src="resolveMediaUrl(song.coverUrl)" alt="" />
      <Music2 v-else :size="20" aria-hidden="true" />
      <span class="search-song-play-overlay">
        <component :is="isCurrent && isPlaying ? Pause : CirclePlay" :size="23" fill="currentColor" aria-hidden="true" />
      </span>
    </button>
    <RouterLink class="search-song-copy" :to="`/songs/${song.id}`">
      <strong><SearchHighlightText :text="song.title" :keyword="keyword" /></strong>
      <small>
        <SearchHighlightText :text="song.artistName || '未知歌手'" :keyword="keyword" />
        <template v-if="song.albumTitle"> · {{ song.albumTitle }}</template>
      </small>
    </RouterLink>
    <span class="search-song-plays">{{ formatCount(song.playCount) }} 次播放</span>
  </article>
</template>

<script setup lang="ts">
import { CirclePlay, Music2, Pause } from "lucide-vue-next";
import type { Song } from "@/api/types";
import { formatCount, resolveMediaUrl } from "@/utils/format";
import SearchHighlightText from "./SearchHighlightText.vue";

withDefaults(defineProps<{
  song: Song;
  keyword?: string;
  isCurrent?: boolean;
  isPlaying?: boolean;
}>(), {
  keyword: "",
  isCurrent: false,
  isPlaying: false
});

defineEmits<{
  toggle: [song: Song];
}>();
</script>
