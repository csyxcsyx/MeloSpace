<template>
  <SongRow
    class="search-song-row"
    :song="song"
    :is-current="isCurrent"
    :is-playing="isPlaying"
    @toggle-play="$emit('toggle', $event)"
  >
    <template #title>
      <RouterLink class="search-song-copy search-song-title" :to="`/songs/${song.id}`">
        <strong><SearchHighlightText :text="song.title" :keyword="keyword" /></strong>
      </RouterLink>
    </template>
    <template #subtitle>
      <RouterLink class="search-song-copy search-song-subtitle" :to="song.artistId ? `/artists/${song.artistId}` : `/songs/${song.id}`">
        <SearchHighlightText :text="song.artistName || '未知歌手'" :keyword="keyword" />
        <template v-if="song.albumTitle"> · {{ song.albumTitle }}</template>
      </RouterLink>
    </template>
    <template #meta>
      <span class="search-song-plays">{{ formatCount(song.playCount) }} 次播放</span>
    </template>
  </SongRow>
</template>

<script setup lang="ts">
import type { Song } from "@/api/types";
import SongRow from "@/components/SongRow.vue";
import { formatCount } from "@/utils/format";
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
