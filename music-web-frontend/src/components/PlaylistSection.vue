<template>
  <section>
    <div class="section-head">
      <h2>{{ title }}</h2>
      <span class="chevron">›</span>
    </div>
    <div class="album-grid">
      <RouterLink v-for="playlist in playlists" :key="playlist.id" class="album" :to="`/playlists/${playlist.id}`">
        <div class="album-cover">
          <img v-if="playlist.coverUrl" :src="resolveMediaUrl(playlist.coverUrl)" alt="" />
          <ListMusic v-else :size="28" />
        </div>
        <div class="album-title">{{ playlist.title }}</div>
        <div class="album-meta">{{ playlist.songCount }} 首 · {{ playlist.visibility === "PUBLIC" ? "公开" : "私有" }}</div>
      </RouterLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ListMusic } from "lucide-vue-next";
import type { Playlist } from "@/api/types";
import { resolveMediaUrl } from "@/utils/format";

defineProps<{
  title: string;
  playlists: Playlist[];
}>();
</script>
