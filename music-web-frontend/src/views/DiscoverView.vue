<template>
  <section class="discover-page">
    <h1 class="page-title">新发现</h1>

    <button class="discover-banner" type="button" @click="scrollToRecommendations">
      <img src="/discover-banner.png" alt="MeloSpace 发现属于你的音乐空间" />
    </button>

    <EmptyState v-if="discover.loading && !discover.loaded">正在加载音乐内容...</EmptyState>
    <template v-else>
      <section class="scene-radio-panel" :style="sceneStyle">
        <div class="section-head scene-radio-head">
          <div>
            <p class="feature-label">场景电台</p>
            <h2>{{ activeScene.title }}</h2>
            <span>{{ activeScene.subtitle }}</span>
          </div>
          <button class="primary-action scene-play-action" type="button" :disabled="!sceneSongs.length" @click="playScene">
            <Play :size="16" fill="currentColor" />
            播放场景
          </button>
        </div>

        <div class="scene-tabs" aria-label="切换场景电台">
          <button
            v-for="scene in discover.sceneDefinitions"
            :key="scene.id"
            type="button"
            :class="{ active: activeScene.id === scene.id }"
            @click="discover.setScene(scene.id)"
          >
            {{ scene.label }}
          </button>
        </div>

        <div class="scene-radio-strip" aria-label="场景推荐歌曲">
          <button
            v-for="song in sceneSpotlightSongs"
            :key="song.id"
            class="scene-song-card"
            type="button"
            @click="toggleSceneSong(song)"
            @dblclick="openPlayer(song)"
          >
            <span class="scene-song-cover">
              <img v-if="song.coverUrl" :src="resolveMediaUrl(song.coverUrl)" alt="" />
              <Music v-else :size="22" />
              <span class="scene-song-play" aria-hidden="true">
                <Pause v-if="player.currentSong?.id === song.id && player.isPlaying" :size="18" fill="currentColor" />
                <Play v-else :size="18" fill="currentColor" />
              </span>
            </span>
            <span class="scene-song-copy">
              <strong>{{ song.title }}</strong>
              <small>{{ song.artistName || "未知歌手" }}</small>
            </span>
          </button>
        </div>
      </section>

      <section ref="recommendationRef" class="discover-recommendations">
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
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Music, Pause, Play, RefreshCw } from "lucide-vue-next";
import type { Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { useDiscoverStore } from "@/stores/discover";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

defineOptions({ name: "DiscoverView" });

const player = usePlayerStore();
const discover = useDiscoverStore();
const router = useRouter();
const recommendationRef = ref<HTMLElement | null>(null);

const recommendedSongs = computed(() => discover.recommendedSongs);
const activeScene = computed(() => discover.activeScene);
const sceneSongs = computed(() => discover.sceneRadioSongs);
const sceneSpotlightSongs = computed(() => sceneSongs.value.slice(0, 4));
const sceneStyle = computed(() => ({
  "--scene-accent": activeScene.value.accent
}));

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
  const queue = queueForSong(song);
  const played = await player.playSong(song, queue);
  if (played) router.push("/player");
}

function playScene() {
  if (!sceneSongs.value.length) return;
  player.playSong(sceneSongs.value[0], sceneSongs.value);
}

function toggleSceneSong(song: Song) {
  if (player.currentSong?.id === song.id) {
    if (player.isPlaying) {
      player.setPlaying(false);
    } else {
      void player.resumeCurrent();
    }
    return;
  }
  player.playSong(song, sceneSongs.value);
}

function queueForSong(song: Song) {
  return sceneSongs.value.some((item) => item.id === song.id) ? sceneSongs.value : recommendedSongs.value;
}
</script>
