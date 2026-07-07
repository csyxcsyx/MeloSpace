<template>
  <div
    ref="rowRef"
    class="song-row"
    :class="{ 'song-row-active': isCurrent }"
    tabindex="0"
    :aria-label="`双击播放 ${song.title} 并打开歌词`"
    @dblclick="$emit('openPlayer', song)"
    @keydown.enter="$emit('openPlayer', song)"
    @keydown.esc.stop="closeMenu"
  >
    <button
      class="song-cover"
      :class="{ 'song-cover-playing': isCurrent && isPlaying }"
      type="button"
      :aria-label="coverLabel"
      :title="coverLabel"
      @click.stop="$emit('togglePlay', song)"
      @dblclick.stop
    >
      <img v-if="song.coverUrl" :src="resolveMediaUrl(song.coverUrl)" alt="" />
      <Music v-else :size="16" />
      <span class="song-cover-action" aria-hidden="true">
        <Pause v-if="isCurrent && isPlaying" :size="18" fill="currentColor" />
        <Play v-else :size="18" fill="currentColor" />
      </span>
    </button>
    <div class="song-info">
      <div class="song-name">{{ song.title }}</div>
      <RouterLink
        v-if="song.artistId"
        class="song-artist song-artist-link"
        :to="`/artists/${song.artistId}`"
        :aria-label="`查看歌手 ${displayName(song.artistName, '未知歌手')}`"
        @click.stop
        @dblclick.stop
      >
        {{ displayName(song.artistName, "未知歌手") }}
      </RouterLink>
      <div v-else class="song-artist">{{ displayName(song.artistName, "未知歌手") }}</div>
    </div>
    <button
      class="more"
      type="button"
      aria-label="更多操作"
      :aria-expanded="menuOpen"
      @click.stop="toggleMenu"
      @dblclick.stop
    >
      <MoreHorizontal :size="18" />
    </button>
    <div v-if="menuOpen" class="song-row-menu" role="menu" @click.stop>
      <button type="button" role="menuitem" @click="favoriteSong">收藏</button>
      <button type="button" role="menuitem" @click="addToPlayQueue">添加到播放列表</button>
      <button type="button" role="menuitem" @click="playSongNext">下一首播放</button>
      <button type="button" role="menuitem" @click="togglePlaylistPicker">添加到歌单</button>
      <button type="button" role="menuitem" @click="downloadSong">下载音乐</button>

      <div v-if="playlistPickerOpen" class="song-row-submenu">
        <p v-if="playlistsLoading" class="menu-muted">正在加载歌单...</p>
        <template v-else-if="playlists.length">
          <button v-for="playlist in playlists" :key="playlist.id" type="button" @click="addToPlaylist(playlist.id)">
            {{ playlist.title }}
          </button>
        </template>
        <button v-else type="button" @click="goCreatePlaylist">还没有歌单，去创建</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { MoreHorizontal, Music, Pause, Play } from "lucide-vue-next";
import { favoriteApi, playlistApi, userApi } from "@/api";
import type { Playlist, Song } from "@/api/types";
import { useAuthStore } from "@/stores/auth";
import { usePlayerStore } from "@/stores/player";
import { useUiStore } from "@/stores/ui";
import { displayName, resolveMediaUrl } from "@/utils/format";

const props = defineProps<{
  song: Song;
  isCurrent?: boolean;
  isPlaying?: boolean;
}>();

const SONG_ROW_MENU_OPEN_EVENT = "melospace-song-row-menu-open";

defineEmits<{
  togglePlay: [song: Song];
  openPlayer: [song: Song];
  more: [song: Song];
}>();

const auth = useAuthStore();
const player = usePlayerStore();
const route = useRoute();
const router = useRouter();
const ui = useUiStore();
const rowRef = ref<HTMLElement | null>(null);
const menuOpen = ref(false);
const playlistPickerOpen = ref(false);
const playlistsLoading = ref(false);
const playlistsLoaded = ref(false);
const playlists = ref<Playlist[]>([]);

const coverLabel = computed(() => {
  if (!props.isCurrent) return `播放 ${props.song.title}`;
  return props.isPlaying ? `暂停 ${props.song.title}` : `继续播放 ${props.song.title}`;
});

onMounted(() => {
  document.addEventListener("click", handleDocumentClick);
  window.addEventListener(SONG_ROW_MENU_OPEN_EVENT, handleAnotherMenuOpen);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", handleDocumentClick);
  window.removeEventListener(SONG_ROW_MENU_OPEN_EVENT, handleAnotherMenuOpen);
});

function handleDocumentClick(event: MouseEvent) {
  if (rowRef.value?.contains(event.target as Node)) return;
  closeMenu();
}

function toggleMenu() {
  const nextOpen = !menuOpen.value;
  menuOpen.value = nextOpen;
  if (nextOpen) {
    window.dispatchEvent(new CustomEvent(SONG_ROW_MENU_OPEN_EVENT, { detail: props.song.id }));
  }
  if (!menuOpen.value) {
    playlistPickerOpen.value = false;
  }
}

function handleAnotherMenuOpen(event: Event) {
  const activeSongId = (event as CustomEvent<number>).detail;
  if (activeSongId === props.song.id) return;
  closeMenu();
}

function closeMenu() {
  menuOpen.value = false;
  playlistPickerOpen.value = false;
}

function requireLogin() {
  if (auth.isAuthenticated) return true;
  ui.toast("请先登录后继续操作");
  router.push({ name: "login", query: { redirect: route.fullPath } });
  closeMenu();
  return false;
}

async function favoriteSong() {
  if (!requireLogin()) return;
  await favoriteApi.add("SONG", props.song.id);
  ui.toast("已收藏歌曲");
  closeMenu();
}

function addToPlayQueue() {
  player.addToQueue(props.song);
  ui.toast("已添加到播放列表");
  closeMenu();
}

function playSongNext() {
  void player.playNext(props.song);
  ui.toast("已设为下一首播放");
  closeMenu();
}

async function togglePlaylistPicker() {
  if (!requireLogin()) return;
  playlistPickerOpen.value = !playlistPickerOpen.value;
  if (!playlistPickerOpen.value || playlistsLoaded.value) return;
  playlistsLoading.value = true;
  try {
    const page = await userApi.playlists(1, 50);
    playlists.value = page.items;
    playlistsLoaded.value = true;
  } finally {
    playlistsLoading.value = false;
  }
}

async function addToPlaylist(playlistId: number) {
  await playlistApi.addSong(playlistId, props.song.id);
  ui.toast("已加入歌单");
  closeMenu();
}

function goCreatePlaylist() {
  router.push("/me");
  closeMenu();
}

function downloadSong() {
  if (!requireLogin()) return;
  const url = resolveMediaUrl(props.song.audioUrl);
  if (!url) {
    ui.toast("暂无可下载音频");
    closeMenu();
    return;
  }
  const link = document.createElement("a");
  link.href = url;
  link.download = `${props.song.title || "melospace-track"}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  ui.toast("已开始下载");
  closeMenu();
}
</script>
