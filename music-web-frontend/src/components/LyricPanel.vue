<template>
  <section class="lyric-panel" :class="{ 'lyric-panel-full': fullscreen }" aria-label="歌词">
    <div v-if="!fullscreen" class="lyric-head">
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
          <template v-if="line.words.length">
            <span
              v-for="(word, wordIndex) in line.words"
              :key="`${word.time}-${wordIndex}-${word.text}`"
              class="lyric-word"
              :style="wordStyle(word, line)"
            >
              {{ word.text }}
            </span>
          </template>
          <template v-else>{{ line.text }}</template>
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
  endTime?: number;
  text: string;
  words: LyricWord[];
}

interface LyricWord {
  time: number;
  endTime?: number;
  text: string;
}

interface TimeToken {
  time: number;
  start: number;
  end: number;
  raw: string;
}

const LYRIC_SYNC_LEAD_SECONDS = 0.28;
const LINE_LOOKAHEAD_SECONDS = 0.08;
const MIN_WORD_DURATION_SECONDS = 0.16;

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
const syncedTime = computed(() => props.currentTime + LYRIC_SYNC_LEAD_SECONDS);
const activeIndex = computed(() => {
  if (!props.isCurrentSong || !lines.value.length) return -1;
  let index = -1;
  for (let i = 0; i < lines.value.length; i += 1) {
    if (lines.value[i].time <= syncedTime.value + LINE_LOOKAHEAD_SECONDS) {
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
  const timestampPattern = /(\[|<)(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?(\]|>)/g;

  for (const rawLine of text.split(/\r?\n/)) {
    const tokens = [...rawLine.matchAll(timestampPattern)].map((match) => toTimeToken(match));
    if (!tokens.length) continue;

    if (hasTokenAfterLyricText(rawLine, tokens)) {
      const line = parseWordTimedLine(rawLine, tokens);
      if (line) parsed.push(line);
      continue;
    }

    const lyricText = rawLine.replace(timestampPattern, "").trim();
    if (!lyricText) continue;

    for (const token of tokens) {
      parsed.push({
        time: token.time,
        text: lyricText,
        words: []
      });
    }
  }

  return parsed.sort((first, second) => first.time - second.time);
}

function toTimeToken(match: RegExpMatchArray): TimeToken {
  const minutes = Number(match[2]);
  const seconds = Number(match[3]);
  const milliseconds = Number((match[4] ?? "0").padEnd(3, "0"));
  const start = match.index ?? 0;
  return {
    time: minutes * 60 + seconds + milliseconds / 1000,
    start,
    end: start + match[0].length,
    raw: match[0]
  };
}

function hasTokenAfterLyricText(rawLine: string, tokens: TimeToken[]) {
  let cursor = 0;
  for (const token of tokens) {
    if (rawLine.slice(cursor, token.start).trim()) {
      return true;
    }
    cursor = token.end;
  }
  return false;
}

function parseWordTimedLine(rawLine: string, tokens: TimeToken[]): LyricLine | null {
  const words: LyricWord[] = [];
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    const nextToken = tokens[index + 1];
    const rawText = rawLine.slice(token.end, nextToken?.start ?? rawLine.length);
    if (!rawText.trim()) continue;

    words.push({
      time: token.time,
      endTime: nextToken?.time,
      text: rawText.replace(/\s+/g, " ")
    });
  }

  const lineText = words.map((word) => word.text).join("").trim();
  if (!lineText) return null;

  return {
    time: tokens[0].time,
    endTime: words[words.length - 1]?.endTime,
    text: lineText,
    words
  };
}

function wordStyle(word: LyricWord, line: LyricLine) {
  return {
    "--lyric-word-progress": `${Math.round(getWordProgress(word, line) * 100)}%`
  };
}

function getWordProgress(word: LyricWord, line: LyricLine) {
  if (!props.isCurrentSong) return 0;
  const startTime = word.time;
  const endTime = Math.max(word.endTime ?? line.endTime ?? startTime + MIN_WORD_DURATION_SECONDS, startTime + MIN_WORD_DURATION_SECONDS);
  if (syncedTime.value <= startTime) return 0;
  if (syncedTime.value >= endTime) return 1;
  return (syncedTime.value - startTime) / (endTime - startTime);
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
  const container = scrollRef.value;
  const line = lineRefs.value[index];
  if (!container || !line) return;

  autoScrolling.value = true;
  const nextTop = line.offsetTop - container.clientHeight / 2 + line.clientHeight / 2;
  container.scrollTo({ top: Math.max(0, nextTop), behavior });
  if (autoScrollTimer) clearTimeout(autoScrollTimer);
  autoScrollTimer = setTimeout(() => {
    autoScrolling.value = false;
  }, 700);
}
</script>
