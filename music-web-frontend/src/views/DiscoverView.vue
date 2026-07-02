<template>
  <section class="discover-page">
    <h1 class="page-title">新发现</h1>

    <button class="discover-banner" type="button" @click="scrollToRecommendations">
      <img src="/discover-banner.png" alt="MeloSpace 发现属于你的音乐空间" />
    </button>

    <EmptyState v-if="discover.loading && !discover.loaded">正在加载音乐内容...</EmptyState>
    <section v-else ref="recommendationRef" class="discover-recommendations">
      <div class="section-head recommendation-head">
        <h2>推荐歌曲</h2>
        <button class="secondary-action refresh-recommendation" type="button" @click="refreshRecommendations">
          <RefreshCw :size="16" />
          刷新推荐
        </button>
      </div>
      <SongColumnList
        v-if="recommendedSongs.length"
        :songs="recommendedSongs"
        :column-count="3"
        @toggle-play="toggleSongPlayback"
        @open-player="openPlayer"
      />
      <EmptyState v-else>还没有可展示的歌曲。</EmptyState>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { RefreshCw } from "lucide-vue-next";
import type { Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { useDiscoverStore } from "@/stores/discover";
import { usePlayerStore } from "@/stores/player";

defineOptions({ name: "DiscoverView" });

const player = usePlayerStore();
const discover = useDiscoverStore();
const router = useRouter();
const recommendationRef = ref<HTMLElement | null>(null);

const recommendedSongs = computed(() => discover.recommendedSongs);

onMounted(() => {
  void discover.load();
});

function refreshRecommendations() {
  discover.refreshRecommendations();
}

function scrollToRecommendations() {
  recommendationRef.value?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function toggleSongPlayback(song: Song) {
  if (player.currentSong?.id === song.id) {
    if (player.isPlaying) {
      player.setPlaying(false);
    } else {
      void player.resumeCurrent();
    }
    return;
  }
  player.playSong(song, recommendedSongs.value);
}

async function openPlayer(song: Song) {
  const played = await player.playSong(song, recommendedSongs.value);
  if (played) router.push("/player");
}
</script>
