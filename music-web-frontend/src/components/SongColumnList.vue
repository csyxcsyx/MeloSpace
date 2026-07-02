<template>
  <div class="song-columns" :style="gridStyle">
    <div v-for="(column, index) in columns" :key="index" class="song-list">
      <SongRow
        v-for="song in column"
        :key="song.id"
        :song="song"
        :is-current="player.currentSong?.id === song.id"
        :is-playing="player.isPlaying"
        @toggle-play="$emit('togglePlay', $event)"
        @open-player="$emit('openPlayer', $event)"
        @more="$emit('more', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Song } from "@/api/types";
import SongRow from "@/components/SongRow.vue";
import { usePlayerStore } from "@/stores/player";

const props = defineProps<{
  songs: Song[];
  columnCount?: number;
}>();

defineEmits<{
  togglePlay: [song: Song];
  openPlayer: [song: Song];
  more: [song: Song];
}>();

const player = usePlayerStore();

const columns = computed(() => {
  const count = props.columnCount ?? 4;
  return Array.from({ length: count }, (_, columnIndex) => props.songs.filter((_, index) => index % count === columnIndex));
});

const gridStyle = computed<Record<string, string>>(() => ({
  "--song-column-count": String(columns.value.length)
}));
</script>
