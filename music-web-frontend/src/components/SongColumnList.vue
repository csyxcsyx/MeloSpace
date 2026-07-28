<template>
  <div class="song-columns" :style="gridStyle" role="list">
    <SongRow
      v-for="song in songs"
      :key="song.id"
      :song="song"
      :is-current="player.currentSong?.id === song.id"
      :is-playing="player.isPlaying"
      role="listitem"
      @toggle-play="$emit('togglePlay', $event)"
      @open-player="$emit('openPlayer', $event)"
      @more="$emit('more', $event)"
    />
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

const columnCount = computed(() => Math.max(1, Math.floor(props.columnCount ?? 4)));

const gridStyle = computed<Record<string, string>>(() => ({
  "--song-column-count": String(columnCount.value),
  "row-gap": "6px"
}));
</script>
