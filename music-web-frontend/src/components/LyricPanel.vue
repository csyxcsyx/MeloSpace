<template>
  <section class="lyric-panel" :class="{ 'lyric-panel-full': fullscreen }" aria-label="歌词">
    <div v-if="!fullscreen" class="lyric-head">
      <div>
        <p class="feature-label">歌词</p>
        <h2>{{ song?.title || "未选择歌曲" }}</h2>
      </div>
      <span class="lyric-clock">{{ isCurrentSong ? formatDuration(currentTime) : "--:--" }}</span>
    </div>

    <div
      ref="scrollRef"
      class="lyric-scroll"
      @scroll="onScroll"
      @wheel.passive="onUserScrollIntent"
      @touchmove.passive="onUserScrollIntent"
    >
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
import { readCachedText } from "@/utils/resourceCache";

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

const LYRIC_LEAD_SECONDS = 0.14;
const MIN_WORD_DURATION_SECONDS = 0.16;
const AUTO_SCROLL_DURATION_MS = 860;
const AUTO_SCROLL_MIN_DELTA = 2;
const DEFAULT_ACTIVE_ANCHOR = 0.5;
const FULLSCREEN_ACTIVE_ANCHOR = 0.36;
const LYRIC_CACHE_LIMIT = 80;

const props = defineProps<{
  song: Song | null;
  currentTime: number;
  isCurrentSong: boolean;
  fullscreen?: boolean;
}>();

const emit = defineEmits<{
  seek: [time: number];
}>();

const lyricLineCache = new Map<string, LyricLine[]>();

const loading = ref(false);
const errorMessage = ref("");
const lines = ref<LyricLine[]>([]);
const lineRefs = ref<HTMLElement[]>([]);
const scrollRef = ref<HTMLElement | null>(null);
const userBrowsing = ref(false);
const autoScrolling = ref(false);
let browsingTimer: ReturnType<typeof setTimeout> | null = null;
let scrollAnimationFrame: number | null = null;
let centerAnimationFrame: number | null = null;
let lastProgrammaticScrollAt = 0;

const lyricUrl = computed(() => props.song?.lyricUrl ?? "");
const syncedTime = computed(() => props.currentTime + LYRIC_LEAD_SECONDS);
const activeIndex = computed(() => {
  if (!props.isCurrentSong || !lines.value.length) return -1;
  let index = -1;
  for (let i = 0; i < lines.value.length; i += 1) {
    if (lines.value[i].time <= syncedTime.value) {
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
  if (props.fullscreen) {
    queueActiveLineCenter("smooth");
  } else {
    scrollToLine(index, "smooth");
  }
});

watch(
  () => props.currentTime,
  () => {
    if (!props.fullscreen || !props.isCurrentSong || userBrowsing.value || activeIndex.value < 0) return;
    if (scrollAnimationFrame !== null) return;
    queueActiveLineCenter("auto");
  },
  { flush: "post" }
);

onBeforeUpdate(() => {
  lineRefs.value = [];
});

onBeforeUnmount(() => {
  if (browsingTimer) clearTimeout(browsingTimer);
  stopAutoScrollAnimation();
  cancelQueuedCentering();
});

async function loadLyrics(url: string) {
  lines.value = [];
  errorMessage.value = "";
  userBrowsing.value = false;
  if (browsingTimer) clearTimeout(browsingTimer);
  if (!url) {
    loading.value = false;
    return;
  }

  const resolvedUrl = resolveMediaUrl(url);
  const cachedLines = lyricLineCache.get(resolvedUrl);
  if (cachedLines) {
    lines.value = cachedLines;
    loading.value = false;
    await nextTick();
    syncAfterLyricLoad();
    return;
  }

  loading.value = true;
  try {
    const text = await readLyricText(resolvedUrl);
    const parsed = parseLrc(text);
    rememberCacheEntry(lyricLineCache, resolvedUrl, parsed);
    lines.value = parsed;
  } catch {
    errorMessage.value = "歌词加载失败。";
  } finally {
    loading.value = false;
    await nextTick();
    syncAfterLyricLoad();
  }
}

async function readLyricText(resolvedUrl: string) {
  return readCachedText(resolvedUrl, LYRIC_CACHE_LIMIT);
}

function rememberCacheEntry<T>(cache: Map<string, T>, key: string, value: T) {
  if (!cache.has(key) && cache.size >= LYRIC_CACHE_LIMIT) {
    const oldestKey = cache.keys().next().value as string | undefined;
    if (oldestKey) cache.delete(oldestKey);
  }
  cache.set(key, value);
}

function syncAfterLyricLoad() {
  if (!errorMessage.value && lines.value.length && props.isCurrentSong && activeIndex.value >= 0) {
    syncToActiveLine("auto");
  } else {
    scrollRef.value?.scrollTo({ top: 0 });
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
  const progress = getWordProgress(word, line);
  return {
    "--lyric-word-progress": `${(progress * 100).toFixed(2)}%`
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

function onScroll() {
  if (autoScrolling.value || !lines.value.length) return;
  if (performance.now() - lastProgrammaticScrollAt < 140) return;
  pauseFollowingForBrowsing();
}

function onUserScrollIntent() {
  pauseFollowingForBrowsing();
}

function pauseFollowingForBrowsing() {
  userBrowsing.value = true;
  stopAutoScrollAnimation();
  cancelQueuedCentering();
  if (browsingTimer) clearTimeout(browsingTimer);
  browsingTimer = setTimeout(() => {
    userBrowsing.value = false;
    if (props.fullscreen && props.isCurrentSong && activeIndex.value >= 0) {
      queueActiveLineCenter("auto");
    }
  }, 4200);
}

async function resumeFollowing() {
  userBrowsing.value = false;
  await nextTick();
  if (activeIndex.value >= 0) {
    scrollToLine(activeIndex.value, "smooth");
  }
}

function syncToActiveLine(behavior: ScrollBehavior) {
  if (activeIndex.value < 0) return;
  scrollToLine(activeIndex.value, behavior);
}

function scrollToLine(index: number, behavior: ScrollBehavior) {
  const container = scrollRef.value;
  const line = lineRefs.value[index];
  if (!container || !line) return;

  const activeAnchor = props.fullscreen ? FULLSCREEN_ACTIVE_ANCHOR : DEFAULT_ACTIVE_ANCHOR;
  const nextTop = line.offsetTop - container.clientHeight * activeAnchor + line.clientHeight / 2;
  const targetTop = Math.max(0, nextTop);

  if (behavior === "smooth") {
    animateScrollTo(container, targetTop);
    return;
  }

  stopAutoScrollAnimation();
  autoScrolling.value = true;
  lastProgrammaticScrollAt = performance.now();
  container.scrollTo({ top: targetTop, behavior });
  requestAnimationFrame(() => {
    autoScrolling.value = false;
  });
}

function queueActiveLineCenter(behavior: ScrollBehavior) {
  if (centerAnimationFrame !== null) return;
  centerAnimationFrame = requestAnimationFrame(() => {
    centerAnimationFrame = null;
    if (userBrowsing.value || activeIndex.value < 0) return;
    scrollToLine(activeIndex.value, behavior);
  });
}

function animateScrollTo(container: HTMLElement, targetTop: number) {
  stopAutoScrollAnimation();

  const startTop = container.scrollTop;
  const distance = targetTop - startTop;
  if (Math.abs(distance) < AUTO_SCROLL_MIN_DELTA) {
    return;
  }

  autoScrolling.value = true;
  const startedAt = performance.now();

  const step = (now: number) => {
    const elapsed = now - startedAt;
    const progress = Math.min(elapsed / AUTO_SCROLL_DURATION_MS, 1);
    const easedProgress = easeInOutCubic(progress);

    lastProgrammaticScrollAt = now;
    container.scrollTop = startTop + distance * easedProgress;

    if (progress < 1) {
      scrollAnimationFrame = requestAnimationFrame(step);
      return;
    }

    container.scrollTop = targetTop;
    autoScrolling.value = false;
    scrollAnimationFrame = null;
  };

  scrollAnimationFrame = requestAnimationFrame(step);
}

function stopAutoScrollAnimation() {
  if (scrollAnimationFrame === null) return;
  cancelAnimationFrame(scrollAnimationFrame);
  scrollAnimationFrame = null;
  autoScrolling.value = false;
}

function cancelQueuedCentering() {
  if (centerAnimationFrame === null) return;
  cancelAnimationFrame(centerAnimationFrame);
  centerAnimationFrame = null;
}

function easeInOutCubic(progress: number) {
  return progress < 0.5
    ? 4 * progress * progress * progress
    : 1 - (-2 * progress + 2) ** 3 / 2;
}
</script>
