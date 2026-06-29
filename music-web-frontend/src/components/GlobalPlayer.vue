<template>
  <footer class="player" aria-label="全局播放器">
    <audio
      ref="audioRef"
      :src="audioSrc"
      :volume="player.volume"
      preload="metadata"
      @loadedmetadata="onLoadedMetadata"
      @timeupdate="onTimeUpdate"
      @ended="player.next"
      @waiting="player.setLoading(true)"
      @canplay="player.setLoading(false)"
      @error="onAudioError"
    />

    <div class="player-controls">
      <button aria-label="上一首" type="button" @click="player.previous">
        <SkipBack :size="18" />
      </button>
      <button class="play-toggle" :aria-label="player.isPlaying ? '暂停' : '播放'" type="button" @click="togglePlay">
        <Pause v-if="player.isPlaying" :size="18" fill="currentColor" />
        <Play v-else :size="18" fill="currentColor" />
      </button>
      <button aria-label="下一首" type="button" @click="player.next">
        <SkipForward :size="18" />
      </button>
    </div>

    <div class="now-playing" :class="{ empty: !player.currentSong }">
      <div class="now-cover">
        <img v-if="player.currentSong?.coverUrl" :src="resolveMediaUrl(player.currentSong.coverUrl)" alt="" />
        <Music v-else :size="18" />
      </div>
      <div class="now-text">
        <div class="now-title">{{ player.currentSong?.title || "选择一首歌开始播放" }}</div>
        <div class="now-meta">
          {{ player.errorMessage || player.currentSong?.artistName || "Orange Music" }}
        </div>
        <div class="mini-progress" aria-hidden="true">
          <span :style="{ width: `${player.progressPercent}%` }" />
        </div>
      </div>
    </div>

    <div class="player-tools">
      <span>{{ formatDuration(player.currentTime) }}</span>
      <Volume2 :size="16" />
      <input
        class="volume-range"
        aria-label="音量"
        type="range"
        min="0"
        max="1"
        step="0.01"
        :value="player.volume"
        @input="setVolume"
      />
      <span>{{ formatDuration(player.duration) }}</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { Music, Pause, Play, SkipBack, SkipForward, Volume2 } from "lucide-vue-next";
import { usePlayerStore } from "@/stores/player";
import { formatDuration, resolveMediaUrl } from "@/utils/format";

const player = usePlayerStore();
const audioRef = ref<HTMLAudioElement | null>(null);
const audioSrc = computed(() => resolveMediaUrl(player.currentSong?.audioUrl));

watch(
  () => player.currentSong?.id,
  async () => {
    await nextTick();
    if (player.isPlaying) {
      playAudio();
    }
  }
);

watch(
  () => player.isPlaying,
  (isPlaying) => {
    if (isPlaying) {
      playAudio();
    } else {
      audioRef.value?.pause();
    }
  }
);

watch(
  () => player.volume,
  (volume) => {
    if (audioRef.value) audioRef.value.volume = volume;
  },
  { immediate: true }
);

function playAudio() {
  const audio = audioRef.value;
  if (!audio || !audioSrc.value) {
    player.setPlaying(false);
    return;
  }
  audio.play().catch(() => {
    player.setError("浏览器阻止了自动播放，请再次点击播放");
  });
}

function togglePlay() {
  if (!player.currentSong) return;
  player.setPlaying(!player.isPlaying);
}

function onLoadedMetadata() {
  const audio = audioRef.value;
  if (!audio) return;
  player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : 0);
}

function onTimeUpdate() {
  const audio = audioRef.value;
  if (!audio) return;
  player.setTime(audio.currentTime, Number.isFinite(audio.duration) ? audio.duration : 0);
}

function onAudioError() {
  player.setError("音频加载失败，请检查媒体文件或代理配置");
}

function setVolume(event: Event) {
  const input = event.target as HTMLInputElement;
  player.setVolume(Number(input.value));
}
</script>
