<template>
  <section class="lyric-panel" :class="{ 'lyric-panel-full': fullscreen }" aria-label="歌词">
    <div class="lyric-head">
      <div>
        <p class="feature-label">歌词</p>
        <h2>{{ song?.title || "未选择歌曲" }}</h2>
      </div>
      <span class="lyric-clock">{{ isCurrentSong ? formatDuration(currentTime) : "--:--" }}</span>
    </div>

    <div ref="scrollRef" class="lyric-scroll" @scroll="onManualScroll">
      <p v-if="loading" class="lyric-state">正在加载歌词...</p>
      <p v-else-if="errorMessage" class="lyric-state">{{ errorMessage }}</p>
      <p v-else-if="!lines.length" class="lyric-state">暂无歌词。</p>
      <template v-else>
        <button
          v-for="(line, index) in lines"
          :key="`${line.time}-${index}`"
          :ref="(element) => setLineRef(element, index)"
          type="button"
          class="lyric-line"
          :class="{ active: index === activeIndex, past: isCurrentSong && index < activeIndex, seekable: Boolean(song) }"
          @click="selectLine(line)"
        >
          {{ line.text }}
        </button>
      </template>
      <button
        v-if="showFollowButton"
        class="lyric-follow-button"
        type="button"
        @click="resumeFollowing"
      >
        回到当前
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onBeforeUpdate,
  ref,
  watch,
  type ComponentPublicInstance
} from "vue";
import type { Song } from "@/api/types";
import { formatDuration, resolveMediaUrl } from "@/utils/format";

interface LyricLine {
  time: number;
  text: string;
}

const props = defineProps<{
  song: Song | null;
  currentTime: number;
  isCurrentSong: boolean;
  fullscreen?: boolean;
}>();

const emit = defineEmits<{
  seek: [time: number];
}>();

const loading = ref(false);
const errorMessage = ref("");
const lines = ref<LyricLine[]>([]);
const lineRefs = ref<HTMLElement[]>([]);
const scrollRef = ref<HTMLElement | null>(null);
const userBrowsing = ref(false);
const autoScrolling = ref(false);
let browsingTimer: ReturnType<typeof setTimeout> | null = null;
let autoScrollTimer: ReturnType<typeof setTimeout> | null = null;

const lyricUrl = computed(() => props.song?.lyricUrl ?? "");
const activeIndex = computed(() => {
  if (!props.isCurrentSong || !lines.value.length) return -1;
  let index = -1;
  for (let i = 0; i < lines.value.length; i += 1) {
    if (lines.value[i].time <= props.currentTime + 0.2) {
      index = i;
    } else {
      break;
    }
  }
  return Math.max(index, 0);
});
const showFollowButton = computed(() => userBrowsing.value && props.isCurrentSong && activeIndex.value >= 0);

watch(lyricUrl, loadLyrics, { immediate: true });

watch(activeIndex, async (index) => {
  if (index < 0 || !props.isCurrentSong) return;
  if (userBrowsing.value) return;
  await nextTick();
  scrollToLine(index, "smooth");
});

onBeforeUpdate(() => {
  lineRefs.value = [];
});

onBeforeUnmount(() => {
  if (browsingTimer) clearTimeout(browsingTimer);
  if (autoScrollTimer) clearTimeout(autoScrollTimer);
});

async function loadLyrics(url: string) {
  lines.value = [];
  errorMessage.value = "";
  if (!url) {
    loading.value = false;
    return;
  }

  loading.value = true;
  try {
    const response = await fetch(resolveMediaUrl(url));
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const text = await response.text();
    lines.value = parseLrc(text);
    await nextTick();
    scrollRef.value?.scrollTo({ top: 0 });
  } catch {
    errorMessage.value = "歌词加载失败。";
  } finally {
    loading.value = false;
  }
}

function parseLrc(text: string) {
  const parsed: LyricLine[] = [];
  const timestampPattern = /\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g;

  for (const rawLine of text.split(/\r?\n/)) {
    const matches = [...rawLine.matchAll(timestampPattern)];
    if (!matches.length) continue;

    const lyricText = rawLine.replace(timestampPattern, "").trim();
    if (!lyricText) continue;

    for (const match of matches) {
      const minutes = Number(match[1]);
      const seconds = Number(match[2]);
      const milliseconds = Number((match[3] ?? "0").padEnd(3, "0"));
      parsed.push({
        time: minutes * 60 + seconds + milliseconds / 1000,
        text: lyricText
      });
    }
  }

  return parsed.sort((first, second) => first.time - second.time);
}

function setLineRef(element: Element | ComponentPublicInstance | null, index: number) {
  if (element instanceof HTMLElement) {
    lineRefs.value[index] = element;
  }
}

function selectLine(line: LyricLine) {
  if (!props.song) return;
  emit("seek", line.time);
}

function onManualScroll() {
  if (autoScrolling.value || !lines.value.length) return;
  userBrowsing.value = true;
  if (browsingTimer) clearTimeout(browsingTimer);
  browsingTimer = setTimeout(() => {
    userBrowsing.value = false;
  }, 4200);
}

async function resumeFollowing() {
  userBrowsing.value = false;
  await nextTick();
  if (activeIndex.value >= 0) {
    scrollToLine(activeIndex.value, "smooth");
  }
}

function scrollToLine(index: number, behavior: ScrollBehavior) {
  autoScrolling.value = true;
  lineRefs.value[index]?.scrollIntoView({ block: "center", behavior });
  if (autoScrollTimer) clearTimeout(autoScrollTimer);
  autoScrollTimer = setTimeout(() => {
    autoScrolling.value = false;
  }, 700);
}
</script>
