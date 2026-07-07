<template>
  <section>
    <PageToolbar />
    <EmptyState v-if="loading">正在加载歌单...</EmptyState>
    <template v-else-if="playlist">
      <section class="detail-hero">
        <div class="detail-cover">
          <img v-if="playlist.coverUrl" :src="resolveMediaUrl(playlist.coverUrl)" alt="" />
          <span v-else>♬</span>
        </div>
        <div class="detail-copy">
          <p class="feature-label">歌单</p>
          <h1 class="page-title">{{ playlist.title }}</h1>
          <p>{{ playlist.description || "这个歌单还没有描述。" }}</p>
          <p class="muted-line">{{ playlist.songs.length }} 首 · {{ playlist.visibility === "PUBLIC" ? "公开" : "私有" }}</p>
          <div class="detail-actions">
            <button type="button" class="primary-action" @click="playAll">播放全部</button>
            <button type="button" class="secondary-action" @click="favorite">收藏</button>
          </div>
        </div>
      </section>

      <section>
        <div class="section-head">
          <div>
            <h2>歌曲</h2>
            <span v-if="canSortPlaylist" class="playlist-sort-status">
              {{ savingOrder ? "正在保存顺序..." : "拖动歌曲即可调整播放顺序" }}
            </span>
          </div>
          <span class="chevron">›</span>
        </div>
        <div v-if="canSortPlaylist" class="playlist-sort-list" :class="{ 'playlist-sort-saving': savingOrder }">
          <div
            v-for="(item, index) in playlistSongs"
            :key="item.id"
            class="playlist-sort-row"
            :class="{
              'playlist-sort-row-dragging': draggedSongId === item.songId,
              'playlist-sort-row-over': dragOverSongId === item.songId
            }"
            :draggable="!savingOrder"
            @dragstart="startDrag($event, item)"
            @dragover.prevent="moveDraggedSong(item)"
            @drop.prevent="dropDraggedSong"
            @dragend="endDrag"
          >
            <span class="playlist-drag-grip" aria-hidden="true">
              <GripVertical :size="18" />
            </span>
            <SongRow
              :song="item.song"
              :is-current="player.currentSong?.id === item.songId"
              :is-playing="player.isPlaying"
              @toggle-play="toggleSongPlayback"
              @open-player="openPlayer"
            />
            <div class="playlist-sort-actions">
              <button
                type="button"
                :disabled="index === 0 || savingOrder"
                :aria-label="`上移 ${item.song.title}`"
                @click="moveSongByButton(index, -1)"
              >
                <ArrowUp :size="15" />
              </button>
              <button
                type="button"
                :disabled="index === playlistSongs.length - 1 || savingOrder"
                :aria-label="`下移 ${item.song.title}`"
                @click="moveSongByButton(index, 1)"
              >
                <ArrowDown :size="15" />
              </button>
            </div>
          </div>
        </div>
        <SongColumnList
          v-else-if="songs.length"
          :songs="songs"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else>歌单还没有歌曲。</EmptyState>
      </section>
    </template>
    <EmptyState v-else>歌单不存在或没有访问权限。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowDown, ArrowUp, GripVertical } from "lucide-vue-next";
import { favoriteApi, playlistApi } from "@/api";
import type { PlaylistDetail, PlaylistSong, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import SongRow from "@/components/SongRow.vue";
import { useAuthStore } from "@/stores/auth";
import { usePlayerStore } from "@/stores/player";
import { useUiStore } from "@/stores/ui";
import { resolveMediaUrl } from "@/utils/format";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const player = usePlayerStore();
const ui = useUiStore();
const loading = ref(true);
const playlist = ref<PlaylistDetail | null>(null);
const playlistSongs = ref<PlaylistSong[]>([]);
const draggedSongId = ref<number | null>(null);
const dragOverSongId = ref<number | null>(null);
const dragSnapshot = ref<PlaylistSong[]>([]);
const dropHandled = ref(false);
const savingOrder = ref(false);
const songs = computed(() => playlistSongs.value.map((item) => item.song));
const canSortPlaylist = computed(() => {
  if (!playlist.value || !auth.user || playlistSongs.value.length < 2) return false;
  return playlist.value.userId === auth.user.id;
});

onMounted(loadPlaylist);

async function loadPlaylist() {
  loading.value = true;
  try {
    playlist.value = await playlistApi.detail(Number(route.params.id));
    playlistSongs.value = playlist.value.songs;
  } finally {
    loading.value = false;
  }
}

function playSong(song: Song) {
  player.playSong(song, songs.value);
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
  player.playSong(song, songs.value);
}

async function openPlayer(song: Song) {
  const played = await player.playSong(song, songs.value);
  if (played) router.push("/player");
}

function playAll() {
  if (!songs.value.length) return;
  player.playSong(songs.value[0], songs.value);
}

async function favorite() {
  if (!playlist.value) return;
  await favoriteApi.add("PLAYLIST", playlist.value.id);
  ui.toast("已收藏歌单");
}

function startDrag(event: DragEvent, item: PlaylistSong) {
  if (!canSortPlaylist.value || savingOrder.value) return;
  draggedSongId.value = item.songId;
  dragOverSongId.value = item.songId;
  dragSnapshot.value = [...playlistSongs.value];
  dropHandled.value = false;
  event.dataTransfer?.setData("text/plain", String(item.songId));
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "move";
  }
}

function moveDraggedSong(target: PlaylistSong) {
  if (!draggedSongId.value || draggedSongId.value === target.songId || savingOrder.value) return;
  dragOverSongId.value = target.songId;
  playlistSongs.value = moveSongBefore(playlistSongs.value, draggedSongId.value, target.songId);
}

async function dropDraggedSong() {
  if (!draggedSongId.value) return;
  dropHandled.value = true;
  const previousItems = [...dragSnapshot.value];
  await persistOrder(previousItems);
  resetDragState();
}

function endDrag() {
  if (!draggedSongId.value || dropHandled.value) return;
  if (dragSnapshot.value.length) {
    playlistSongs.value = [...dragSnapshot.value];
  }
  resetDragState();
}

async function moveSongByButton(index: number, direction: -1 | 1) {
  if (!canSortPlaylist.value || savingOrder.value) return;
  const nextIndex = index + direction;
  if (nextIndex < 0 || nextIndex >= playlistSongs.value.length) return;
  const previousItems = [...playlistSongs.value];
  const nextItems = [...playlistSongs.value];
  [nextItems[index], nextItems[nextIndex]] = [nextItems[nextIndex], nextItems[index]];
  playlistSongs.value = nextItems;
  await persistOrder(previousItems);
}

function moveSongBefore(items: PlaylistSong[], draggedId: number, targetId: number) {
  const currentIndex = items.findIndex((item) => item.songId === draggedId);
  const targetIndex = items.findIndex((item) => item.songId === targetId);
  if (currentIndex < 0 || targetIndex < 0) return items;
  const nextItems = [...items];
  const [draggedItem] = nextItems.splice(currentIndex, 1);
  nextItems.splice(targetIndex, 0, draggedItem);
  return nextItems;
}

async function persistOrder(previousItems: PlaylistSong[]) {
  if (!playlist.value || orderKey(previousItems) === orderKey(playlistSongs.value)) return;

  savingOrder.value = true;
  try {
    const updated = await playlistApi.reorder(playlist.value.id, playlistSongs.value.map((item) => item.songId));
    playlist.value = updated;
    playlistSongs.value = updated.songs;
    ui.toast("歌单顺序已保存");
  } catch {
    playlistSongs.value = previousItems;
    ui.toast("排序保存失败，已恢复原顺序");
  } finally {
    savingOrder.value = false;
  }
}

function orderKey(items: PlaylistSong[]) {
  return items.map((item) => item.songId).join(",");
}

function resetDragState() {
  draggedSongId.value = null;
  dragOverSongId.value = null;
  dragSnapshot.value = [];
  dropHandled.value = false;
}
</script>
