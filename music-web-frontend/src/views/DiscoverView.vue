<template>
  <section class="discover-page">
    <header class="daily-hero">
      <div>
        <p class="feature-label">MeloSpace Daily</p>
        <h1 class="page-title">每日推荐</h1>
        <p class="daily-subtitle">{{ todayLabel }} · {{ dailySongs.length }} 首</p>
      </div>
      <button class="daily-refresh" type="button" @click="shuffleDailySongs">
        <Shuffle :size="17" />
        <span>换一组</span>
      </button>
    </header>

    <EmptyState v-if="loading">正在加载音乐内容...</EmptyState>
    <section v-else-if="dailySongs.length" class="daily-card-grid" aria-label="每日推荐歌曲">
      <article
        v-for="(song, index) in dailySongs"
        :key="song.id"
        class="lyric-song-card"
        :class="{ active: player.currentSong?.id === song.id }"
        :style="{ '--card-accent': accentFor(index), '--delay': `${index * 58}ms` }"
      >
        <button class="lyric-card-cover" type="button" :aria-label="coverLabel(song)" @click="toggleSongPlayback(song)">
          <img v-if="song.coverUrl" :src="resolveMediaUrl(song.coverUrl)" alt="" />
          <Music v-else :size="28" />
          <span class="lyric-card-play">
            <Pause v-if="player.currentSong?.id === song.id && player.isPlaying" :size="22" fill="currentColor" />
            <Play v-else :size="22" fill="currentColor" />
          </span>
        </button>

        <div class="lyric-card-copy">
          <div>
            <p class="feature-label">{{ song.genre || song.mood || "Daily Pick" }}</p>
            <h2>{{ song.title }}</h2>
            <p class="daily-artist">{{ song.artistName || "未知歌手" }}</p>
          </div>

          <div class="lyric-preview" aria-hidden="true">
            <p v-for="line in previewLines(song, index)" :key="line">{{ line }}</p>
          </div>
        </div>

        <div class="lyric-card-actions">
          <button type="button" @click="toggleSongPlayback(song)">
            <Pause v-if="player.currentSong?.id === song.id && player.isPlaying" :size="16" fill="currentColor" />
            <Play v-else :size="16" fill="currentColor" />
            <span>{{ player.currentSong?.id === song.id && player.isPlaying ? "暂停" : "播放" }}</span>
          </button>
          <button type="button" @click="openPlayer(song)">
            <Maximize2 :size="16" />
            <span>歌词</span>
          </button>
        </div>
      </article>
    </section>
    <EmptyState v-else>还没有可展示的歌曲。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { Maximize2, Music, Pause, Play, Shuffle } from "lucide-vue-next";
import { songApi } from "@/api";
import type { Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

const player = usePlayerStore();
const router = useRouter();
const loading = ref(true);
const songs = ref<Song[]>([]);
const dailySongs = ref<Song[]>([]);
const shuffleOffset = ref(0);

const todayLabel = computed(() => {
  const now = new Date();
  return `${now.getMonth() + 1}月${now.getDate()}日`;
});

onMounted(loadDiscover);

async function loadDiscover() {
  loading.value = true;
  try {
    const songPage = await songApi.list({ page: 1, size: 30 });
    songs.value = songPage.items;
    setDailySongs();
  } finally {
    loading.value = false;
  }
}

function setDailySongs() {
  const seed = daySeed() + shuffleOffset.value * 9973;
  dailySongs.value = seededShuffle(songs.value, seed).slice(0, 6);
}

function shuffleDailySongs() {
  shuffleOffset.value += 1;
  setDailySongs();
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
  void player.playSong(song, dailySongs.value);
}

function openPlayer(song: Song) {
  void player.playSong(song, dailySongs.value);
  router.push("/player");
}

function coverLabel(song: Song) {
  if (player.currentSong?.id !== song.id) return `播放 ${song.title}`;
  return player.isPlaying ? `暂停 ${song.title}` : `继续播放 ${song.title}`;
}

function previewLines(song: Song, index: number) {
  const artist = song.artistName || "MeloSpace";
  const mood = song.mood || song.genre || "今天";
  const templates = [
    [`把 ${song.title} 放进耳机`, `让 ${artist} 的声音靠近`, `${mood} 的光落在这一刻`],
    [`城市慢慢安静下来`, `${song.title} 还在循环`, `你听见 ${artist} 的呼吸`],
    [`今天的风有一点亮`, `${mood} 刚好经过`, `${song.title} 留在心里`],
    [`按下播放以后`, `世界变得轻一点`, `${artist} 唱着 ${song.title}`]
  ];
  return templates[index % templates.length];
}

function accentFor(index: number) {
  const accents = ["#fa233b", "#ff8a2a", "#1fb6a6", "#5f7cff", "#b45cff", "#2f9bff"];
  return accents[index % accents.length];
}

function daySeed() {
  const now = new Date();
  return now.getFullYear() * 10000 + (now.getMonth() + 1) * 100 + now.getDate();
}

function seededShuffle<T>(items: T[], seed: number) {
  const result = [...items];
  let nextSeed = seed;
  for (let index = result.length - 1; index > 0; index -= 1) {
    nextSeed = (nextSeed * 9301 + 49297) % 233280;
    const swapIndex = Math.floor((nextSeed / 233280) * (index + 1));
    [result[index], result[swapIndex]] = [result[swapIndex], result[index]];
  }
  return result;
}
</script>
