<template>
  <div class="song-columns">
    <div v-for="(column, index) in columns" :key="index" class="song-list">
      <SongRow v-for="song in column" :key="song.id" :song="song" @play="$emit('play', $event)" @more="$emit('more', $event)" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { Song } from "@/api/types";
import SongRow from "@/components/SongRow.vue";

const props = defineProps<{
  songs: Song[];
  columnCount?: number;
}>();

defineEmits<{
  play: [song: Song];
  more: [song: Song];
}>();

const columns = computed(() => {
  const count = props.columnCount ?? 4;
  return Array.from({ length: count }, (_, columnIndex) => props.songs.filter((_, index) => index % count === columnIndex));
});
</script>
