<template>
  <div class="song-list" role="list">
    <div v-if="showHeader" class="song-list-header" aria-hidden="true">
      <span class="song-list-header-identity">歌名 / 歌手</span>
      <span class="song-list-header-meta">
        <span>专辑</span>
        <span>时长</span>
      </span>
      <span></span>
      <span></span>
    </div>
    <SongRow
      v-for="(song, index) in songs"
      :key="song.id"
      :song="song"
      :is-current="player.currentSong?.id === song.id"
      :is-playing="player.isPlaying"
      :favorited="favoritedSongIds?.has(song.id)"
      role="listitem"
      @toggle-play="$emit('togglePlay', $event)"
      @open-player="$emit('openPlayer', $event)"
      @favorite-change="(changedSong, favorited) => $emit('favoriteChange', changedSong, favorited)"
    >
      <template v-if="$slots.leading" #leading="slotProps">
        <slot name="leading" v-bind="slotProps" :index="index" />
      </template>
      <template v-if="$slots.title" #title="slotProps">
        <slot name="title" v-bind="slotProps" :index="index" />
      </template>
      <template v-if="$slots.subtitle" #subtitle="slotProps">
        <slot name="subtitle" v-bind="slotProps" :index="index" />
      </template>
      <template v-if="$slots.meta" #meta="slotProps">
        <slot name="meta" v-bind="slotProps" :index="index" />
      </template>
      <template v-if="$slots.actions" #actions="slotProps">
        <slot name="actions" v-bind="slotProps" :index="index" />
      </template>
    </SongRow>
  </div>
</template>

<script setup lang="ts">
import type { Song } from "@/api/types";
import SongRow from "@/components/SongRow.vue";
import { usePlayerStore } from "@/stores/player";

withDefaults(defineProps<{
  songs: Song[];
  favoritedSongIds?: ReadonlySet<number>;
  showHeader?: boolean;
}>(), {
  showHeader: true
});

defineEmits<{
  togglePlay: [song: Song];
  openPlayer: [song: Song];
  favoriteChange: [song: Song, favorited: boolean];
}>();

const player = usePlayerStore();
</script>
