<template>
  <div class="song-row">
    <button class="song-cover" type="button" :aria-label="`播放 ${song.title}`" @click="$emit('play', song)">
      <img v-if="song.coverUrl" :src="resolveMediaUrl(song.coverUrl)" alt="" />
      <Music v-else :size="16" />
    </button>
    <RouterLink class="song-info" :to="`/songs/${song.id}`">
      <div class="song-name">{{ song.title }}</div>
      <div class="song-artist">{{ displayName(song.artistName, "未知歌手") }}</div>
    </RouterLink>
    <button class="more" type="button" aria-label="更多操作" @click="$emit('more', song)">
      <MoreHorizontal :size="18" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { MoreHorizontal, Music } from "lucide-vue-next";
import type { Song } from "@/api/types";
import { displayName, resolveMediaUrl } from "@/utils/format";

defineProps<{ song: Song }>();
defineEmits<{
  play: [song: Song];
  more: [song: Song];
}>();
</script>
