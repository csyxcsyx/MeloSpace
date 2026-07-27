<template>
  <div ref="rootRef" class="song-actions">
    <button
      ref="triggerRef"
      class="song-actions-trigger"
      :class="`song-actions-trigger-${variant}`"
      type="button"
      :aria-label="`更多操作：${song.title}`"
      :aria-expanded="menuOpen"
      aria-haspopup="menu"
      @click.stop="toggleMenu"
      @dblclick.stop
      @keydown.esc.stop="closeMenu"
    >
      <MoreHorizontal :size="variant === 'player' ? 21 : 18" />
    </button>

    <Teleport to="body">
      <Transition name="song-actions-pop">
        <section
          v-if="menuOpen"
          ref="menuRef"
          class="song-actions-menu"
          :class="`song-actions-menu-${variant}`"
          :style="menuStyle"
          role="menu"
          :aria-label="`${song.title} 的歌曲操作`"
          @click.stop
          @dblclick.stop
          @keydown.esc.stop="closeMenu"
        >
          <header class="song-actions-menu-head">
            <strong>{{ song.title }}</strong>
            <span>{{ song.artistName || "未知歌手" }}</span>
          </header>

          <button type="button" role="menuitem" @click="favoriteSong">
            <Heart :size="17" />
            <span>收藏</span>
          </button>
          <button type="button" role="menuitem" @click="addToPlayQueue">
            <ListMusic :size="17" />
            <span>添加到播放列表</span>
          </button>
          <button type="button" role="menuitem" @click="playSongNext">
            <ListPlus :size="17" />
            <span>下一首播放</span>
          </button>
          <button type="button" role="menuitem" :aria-expanded="playlistPickerOpen" @click="togglePlaylistPicker">
            <FolderPlus :size="17" />
            <span>添加到歌单</span>
            <ChevronDown class="song-actions-chevron" :class="{ open: playlistPickerOpen }" :size="15" />
          </button>

          <div v-if="playlistPickerOpen" class="song-actions-submenu">
            <p v-if="playlistsLoading" class="menu-muted">正在加载歌单...</p>
            <template v-else-if="playlists.length">
              <button v-for="playlist in playlists" :key="playlist.id" type="button" @click="addToPlaylist(playlist.id)">
                <span>{{ playlist.title }}</span>
              </button>
            </template>
            <button v-else type="button" @click="goCreatePlaylist">
              <span>还没有歌单，去创建</span>
            </button>
          </div>

          <button type="button" role="menuitem" @click="downloadSong">
            <Download :size="17" />
            <span>下载音乐</span>
          </button>
        </section>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ChevronDown, Download, FolderPlus, Heart, ListMusic, ListPlus, MoreHorizontal } from "lucide-vue-next";
import { favoriteApi, playlistApi, userApi } from "@/api";
import type { Playlist, Song } from "@/api/types";
import { useAuthStore } from "@/stores/auth";
import { usePlayerStore } from "@/stores/player";
import { useUiStore } from "@/stores/ui";
import { resolveMediaUrl } from "@/utils/format";

const props = withDefaults(defineProps<{
  song: Song;
  variant?: "row" | "player";
}>(), {
  variant: "row"
});

const MENU_OPEN_EVENT = "melospace-song-actions-menu-open";
const auth = useAuthStore();
const player = usePlayerStore();
const route = useRoute();
const router = useRouter();
const ui = useUiStore();
const rootRef = ref<HTMLElement | null>(null);
const triggerRef = ref<HTMLButtonElement | null>(null);
const menuRef = ref<HTMLElement | null>(null);
const menuOpen = ref(false);
const playlistPickerOpen = ref(false);
const playlistsLoading = ref(false);
const playlistsLoaded = ref(false);
const playlists = ref<Playlist[]>([]);
const menuStyle = ref<Record<string, string>>({});
const menuInstanceId = Symbol(`song-actions-${props.song.id}`);

onMounted(() => {
  document.addEventListener("pointerdown", handleDocumentPointerDown);
  window.addEventListener(MENU_OPEN_EVENT, handleAnotherMenuOpen);
  window.addEventListener("resize", handleViewportChange);
  window.addEventListener("scroll", handleWindowScroll, true);
});

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", handleDocumentPointerDown);
  window.removeEventListener(MENU_OPEN_EVENT, handleAnotherMenuOpen);
  window.removeEventListener("resize", handleViewportChange);
  window.removeEventListener("scroll", handleWindowScroll, true);
});

async function toggleMenu() {
  if (menuOpen.value) {
    closeMenu();
    return;
  }
  menuOpen.value = true;
  window.dispatchEvent(new CustomEvent(MENU_OPEN_EVENT, { detail: menuInstanceId }));
  await nextTick();
  positionMenu();
}

function closeMenu() {
  menuOpen.value = false;
  playlistPickerOpen.value = false;
  menuStyle.value = {};
}

function handleDocumentPointerDown(event: PointerEvent) {
  const target = event.target as Node;
  if (rootRef.value?.contains(target) || menuRef.value?.contains(target)) return;
  closeMenu();
}

function handleAnotherMenuOpen(event: Event) {
  if ((event as CustomEvent<symbol>).detail === menuInstanceId) return;
  closeMenu();
}

function handleViewportChange() {
  if (!menuOpen.value) return;
  positionMenu();
}

function handleWindowScroll(event: Event) {
  if (!menuOpen.value || menuRef.value?.contains(event.target as Node)) return;
  closeMenu();
}

function positionMenu() {
  if (window.innerWidth <= 820) {
    menuStyle.value = {};
    return;
  }
  const trigger = triggerRef.value;
  const menu = menuRef.value;
  if (!trigger || !menu) return;
  const triggerRect = trigger.getBoundingClientRect();
  const menuRect = menu.getBoundingClientRect();
  const edge = 12;
  const left = Math.min(
    window.innerWidth - menuRect.width - edge,
    Math.max(edge, triggerRect.right - menuRect.width)
  );
  const spaceBelow = window.innerHeight - triggerRect.bottom - edge;
  const top = spaceBelow >= menuRect.height
    ? triggerRect.bottom + 8
    : Math.max(edge, triggerRect.top - menuRect.height - 8);
  menuStyle.value = {
    left: `${Math.round(left)}px`,
    top: `${Math.round(top)}px`
  };
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
  await nextTick();
  positionMenu();
  if (!playlistPickerOpen.value || playlistsLoaded.value) return;
  playlistsLoading.value = true;
  try {
    const page = await userApi.playlists(1, 50);
    playlists.value = page.items;
    playlistsLoaded.value = true;
  } finally {
    playlistsLoading.value = false;
    await nextTick();
    positionMenu();
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
  link.download = props.song.title || "melospace-track";
  document.body.appendChild(link);
  link.click();
  link.remove();
  ui.toast("已开始下载");
  closeMenu();
}
</script>
