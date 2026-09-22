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
        <strong>{{ song.title }}</strong>
      </RouterLink>
    </template>
    <template #subtitle>
      <RouterLink class="search-song-copy search-song-subtitle search-song-subtitle-link" :to="song.artistId ? `/artists/${song.artistId}` : `/songs/${song.id}`">
        {{ song.artistName || "未知歌手" }}
        <template v-if="song.albumTitle"> · {{ song.albumTitle }}</template>
      </RouterLink>
      <span class="search-song-copy search-song-subtitle search-song-subtitle-mobile">
        {{ song.artistName || "未知歌手" }}
        <template v-if="song.albumTitle"> · {{ song.albumTitle }}</template>
      </span>
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

<style scoped>
.search-song-subtitle-mobile {
  display: none;
}

@media (max-width: 760px) {
  .search-song-subtitle-link {
    display: none;
  }

  .search-song-subtitle-mobile {
    display: block;
  }
}
</style>
