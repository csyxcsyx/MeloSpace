<template>
  <section class="playlist-community-page">
    <PageToolbar />
    <EmptyState v-if="loading">正在加载歌单...</EmptyState>
    <template v-else-if="playlist">
      <section class="playlist-community-hero">
        <div class="detail-cover playlist-community-cover">
          <img v-if="playlist.coverUrl" :src="resolveMediaUrl(playlist.coverUrl)" :alt="`${playlist.title} 封面`" />
          <span v-else aria-hidden="true">♬</span>
        </div>
        <div class="detail-copy playlist-community-copy">
          <p class="feature-label">歌单 · {{ playlist.visibility === "PUBLIC" ? "公开" : "私有" }}</p>
          <h1 class="page-title">{{ playlist.title }}</h1>
          <p class="playlist-description">{{ playlist.description || "这个歌单还没有描述。" }}</p>
          <RouterLink class="playlist-creator" :to="`/users/${playlist.userId}`">
            <span class="playlist-creator-avatar">
              <img
                v-if="playlist.creatorAvatarUrl"
                :src="resolveMediaUrl(playlist.creatorAvatarUrl)"
                alt=""
              />
              <span v-else>{{ creatorInitial }}</span>
            </span>
            <strong>{{ playlist.creatorNickname || "MeloSpace 用户" }}</strong>
          </RouterLink>
          <div v-if="playlist.tags.length" class="playlist-tags" aria-label="歌单标签">
            <span v-for="tag in playlist.tags" :key="tag"># {{ tag }}</span>
          </div>
          <p class="playlist-meta-line">
            <span>{{ playlist.songCount }} 首</span>
            <span>{{ compactNumber(playlist.playCount) }} 次播放</span>
            <span>{{ compactNumber(playlist.favoriteCount) }} 人收藏</span>
            <span>{{ compactNumber(playlist.commentCount) }} 条评论</span>
            <span>更新于 {{ formatDate(playlist.updatedAt) }}</span>
          </p>
          <div class="detail-actions playlist-primary-actions">
            <button type="button" class="primary-action" :disabled="!playlistSongs.length" @click="playAll">
              <Play :size="18" fill="currentColor" />
              播放全部
            </button>
            <button type="button" class="secondary-action" :disabled="!playlistSongs.length" @click="shufflePlay">
              <Shuffle :size="18" />
              随机播放
            </button>
            <button
              v-if="!playlist.canManage"
              type="button"
              class="secondary-action"
              :class="{ 'is-active': playlist.favorited }"
              :disabled="favoriteSaving"
              :aria-pressed="playlist.favorited"
              @click="toggleFavorite"
            >
              <Heart :size="18" :fill="playlist.favorited ? 'currentColor' : 'none'" />
              {{ playlist.favorited ? "已收藏" : "收藏" }}
            </button>
            <button type="button" class="secondary-action" @click="sharePlaylist">
              <Share2 :size="18" />
              分享
            </button>
            <button
              v-if="playlist.canManage"
              type="button"
              class="secondary-action"
              :aria-expanded="editing"
              @click="toggleEditor"
            >
              <Pencil :size="18" />
              编辑歌单
            </button>
          </div>
        </div>
      </section>

      <form v-if="playlist.canManage && editing" class="playlist-editor glass-panel" @submit.prevent="savePlaylist">
        <div class="section-head playlist-panel-head">
          <div>
            <p class="feature-label">管理歌单</p>
            <h2>编辑资料</h2>
          </div>
          <button type="button" class="playlist-icon-button" aria-label="关闭编辑" @click="editing = false">
            <X :size="19" />
          </button>
        </div>
        <div class="playlist-editor-grid">
          <label>
            <span>歌单名称</span>
            <input v-model.trim="editForm.title" maxlength="100" required />
          </label>
          <label>
            <span>公开状态</span>
            <select v-model="editForm.visibility">
              <option value="PUBLIC">公开，所有人可发现与评论</option>
              <option value="PRIVATE">私有，仅自己可见</option>
            </select>
          </label>
          <label class="playlist-editor-wide">
            <span>描述</span>
            <textarea v-model.trim="editForm.description" maxlength="500" rows="4" />
            <small>{{ editForm.description.length }}/500</small>
          </label>
          <label class="playlist-editor-wide">
            <span>标签（使用逗号分隔，最多 5 个，每个最多 12 字）</span>
            <input v-model="editForm.tagsText" maxlength="69" placeholder="例如：华语流行, 通勤, 治愈" />
          </label>
          <div class="playlist-editor-wide playlist-cover-field">
            <span>歌单封面</span>
            <div class="playlist-cover-controls">
              <label class="secondary-action playlist-file-button">
                <ImagePlus :size="18" />
                {{ coverUploading ? "正在上传..." : "上传图片" }}
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  :disabled="coverUploading"
                  @change="uploadCover"
                />
              </label>
              <input v-model.trim="editForm.coverUrl" aria-label="歌单封面地址" placeholder="或填写图片地址" />
            </div>
          </div>
        </div>
        <div class="playlist-editor-actions">
          <button type="button" class="secondary-action" @click="resetEditor">恢复</button>
          <button type="submit" class="primary-action" :disabled="editSaving || coverUploading">
            {{ editSaving ? "正在保存..." : "保存修改" }}
          </button>
        </div>
      </form>

      <section class="playlist-song-section">
        <div class="section-head playlist-panel-head">
          <div>
            <h2>歌曲</h2>
            <span v-if="canReorder" class="playlist-sort-status">
              {{ savingOrder ? "正在保存顺序..." : "桌面拖动排序，手机可使用上下移动按钮" }}
            </span>
            <span v-else-if="playlist.canManage && (songQuery || songSort !== 'CUSTOM')" class="playlist-sort-status">
              清除搜索并切换到“歌单顺序”后可调整顺序
            </span>
          </div>
          <button
            v-if="playlist.canManage"
            type="button"
            class="secondary-action"
            :aria-expanded="addingSongs"
            @click="toggleSongPicker"
          >
            <ListPlus :size="18" />
            批量添加
          </button>
        </div>

        <div class="playlist-song-tools glass-panel">
          <label class="playlist-search-field">
            <Search :size="18" aria-hidden="true" />
            <span class="sr-only">搜索歌单内歌曲</span>
            <input v-model.trim="songQuery" type="search" placeholder="搜索歌名、歌手或专辑" />
            <button v-if="songQuery" type="button" aria-label="清除歌曲搜索" @click="songQuery = ''">
              <X :size="17" />
            </button>
          </label>
          <label class="playlist-sort-field">
            <span>排序</span>
            <select v-model="songSort">
              <option value="CUSTOM">歌单顺序</option>
              <option value="TITLE">歌曲名称</option>
              <option value="ARTIST">歌手名称</option>
              <option value="NEWEST">最近添加</option>
            </select>
          </label>
          <button
            v-if="playlist.canManage && selectedSongIds.size"
            type="button"
            class="playlist-danger-button"
            :disabled="batchRemoving"
            @click="removeSelectedSongs"
          >
            <Trash2 :size="17" />
            移除所选（{{ selectedSongIds.size }}）
          </button>
        </div>

        <section v-if="playlist.canManage && addingSongs" class="playlist-song-picker glass-panel">
          <div class="section-head playlist-panel-head">
            <div>
              <p class="feature-label">添加歌曲</p>
              <h3>从曲库批量选择</h3>
            </div>
            <button type="button" class="playlist-icon-button" aria-label="关闭歌曲选择" @click="addingSongs = false">
              <X :size="19" />
            </button>
          </div>
          <form class="playlist-picker-search" @submit.prevent="searchCandidates">
            <label>
              <Search :size="18" aria-hidden="true" />
              <span class="sr-only">搜索曲库</span>
              <input v-model.trim="candidateKeyword" type="search" placeholder="输入歌名搜索曲库" />
            </label>
            <button type="submit" class="secondary-action" :disabled="candidateLoading">
              {{ candidateLoading ? "搜索中..." : "搜索" }}
            </button>
          </form>
          <div v-if="candidateLoading" class="playlist-picker-status">正在加载歌曲...</div>
          <div v-else-if="availableCandidates.length" class="playlist-candidate-list">
            <label v-for="song in availableCandidates" :key="song.id" class="playlist-candidate-row">
              <input
                type="checkbox"
                :checked="candidateSongIds.has(song.id)"
                @change="toggleCandidate(song.id)"
              />
              <img v-if="song.coverUrl" :src="resolveMediaUrl(song.coverUrl)" alt="" />
              <span v-else class="playlist-candidate-cover">♪</span>
              <span>
                <strong>{{ song.title }}</strong>
                <small>{{ song.artistName || "未知歌手" }} · {{ song.albumTitle || "单曲" }}</small>
              </span>
            </label>
          </div>
          <EmptyState v-else>没有可添加的歌曲，试试其他关键词。</EmptyState>
          <div class="playlist-editor-actions">
            <span class="muted-line">已选择 {{ candidateSongIds.size }} 首</span>
            <button
              type="button"
              class="primary-action"
              :disabled="!candidateSongIds.size || batchAdding"
              @click="addSelectedSongs"
            >
              {{ batchAdding ? "正在添加..." : "添加到歌单" }}
            </button>
          </div>
        </section>

        <div
          v-if="playlist.canManage && filteredPlaylistSongs.length"
          class="playlist-sort-list"
          :class="{ 'playlist-sort-saving': savingOrder }"
        >
          <div
            v-for="(item, index) in filteredPlaylistSongs"
            :key="item.id"
            class="playlist-sort-row playlist-manage-row"
            :class="{
              'playlist-sort-row-dragging': draggedSongId === item.songId,
              'playlist-sort-row-over': dragOverSongId === item.songId
            }"
            :draggable="canReorder && !savingOrder"
            @dragstart="startDrag($event, item)"
            @dragover.prevent="moveDraggedSong(item)"
            @drop.prevent="dropDraggedSong"
            @dragend="endDrag"
          >
            <label class="playlist-select-song" :aria-label="`选择 ${item.song.title}`">
              <input
                type="checkbox"
                :checked="selectedSongIds.has(item.songId)"
                @change="toggleSelectedSong(item.songId)"
              />
            </label>
            <span class="playlist-drag-grip" :class="{ 'is-disabled': !canReorder }" aria-hidden="true">
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
                :disabled="!canReorder || index === 0 || savingOrder"
                :aria-label="`上移 ${item.song.title}`"
                @click="moveSongByButton(item.songId, -1)"
              >
                <ArrowUp :size="17" />
              </button>
              <button
                type="button"
                :disabled="!canReorder || index === filteredPlaylistSongs.length - 1 || savingOrder"
                :aria-label="`下移 ${item.song.title}`"
                @click="moveSongByButton(item.songId, 1)"
              >
                <ArrowDown :size="17" />
              </button>
              <button
                type="button"
                :disabled="savingOrder"
                :aria-label="`从歌单移除 ${item.song.title}`"
                @click="removeOneSong(item.songId)"
              >
                <Trash2 :size="17" />
              </button>
            </div>
          </div>
        </div>
        <SongColumnList
          v-else-if="filteredSongs.length"
          :songs="filteredSongs"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else-if="playlistSongs.length">没有找到匹配的歌曲。</EmptyState>
        <EmptyState v-else>歌单还没有歌曲。</EmptyState>
      </section>

      <CommentThread
        v-if="playlist.visibility === 'PUBLIC'"
        :target-id="playlist.id"
        target-type="PLAYLIST"
      />
      <section v-else class="playlist-private-note glass-panel">
        <Lock :size="20" />
        <div>
          <strong>私有歌单不会展示社区评论</strong>
          <p>已有评论会被保留，重新公开后可继续查看。</p>
        </div>
      </section>
    </template>
    <EmptyState v-else>歌单不存在或没有访问权限。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ArrowDown,
  ArrowUp,
  GripVertical,
  Heart,
  ImagePlus,
  ListPlus,
  Lock,
  Pencil,
  Play,
  Search,
  Share2,
  Shuffle,
  Trash2,
  X
} from "lucide-vue-next";
import { favoriteApi, playlistApi, songApi, uploadApi } from "@/api";
import type { PlaylistDetail, PlaylistSong, Song } from "@/api/types";
import CommentThread from "@/components/CommentThread.vue";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import SongRow from "@/components/SongRow.vue";
import { useAuthStore } from "@/stores/auth";
import { usePlayerStore } from "@/stores/player";
import { useUiStore } from "@/stores/ui";
import { resolveMediaUrl } from "@/utils/format";
import { applyPlaylistFavorite, canManagePlaylist } from "@/utils/playlist";

type SongSort = "CUSTOM" | "TITLE" | "ARTIST" | "NEWEST";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const player = usePlayerStore();
const ui = useUiStore();
const loading = ref(true);
const playlist = ref<PlaylistDetail | null>(null);
const playlistSongs = ref<PlaylistSong[]>([]);
const editing = ref(false);
const editSaving = ref(false);
const coverUploading = ref(false);
const favoriteSaving = ref(false);
const addingSongs = ref(false);
const candidateLoading = ref(false);
const batchAdding = ref(false);
const batchRemoving = ref(false);
const songQuery = ref("");
const songSort = ref<SongSort>("CUSTOM");
const candidateKeyword = ref("");
const candidateSongs = ref<Song[]>([]);
const selectedSongIds = ref(new Set<number>());
const candidateSongIds = ref(new Set<number>());
const draggedSongId = ref<number | null>(null);
const dragOverSongId = ref<number | null>(null);
const dragSnapshot = ref<PlaylistSong[]>([]);
const dropHandled = ref(false);
const savingOrder = ref(false);
const editForm = reactive({
  title: "",
  description: "",
  coverUrl: "",
  visibility: "PUBLIC" as "PUBLIC" | "PRIVATE",
  tagsText: ""
});

const normalizedSongQuery = computed(() => songQuery.value.trim().toLocaleLowerCase());
const filteredPlaylistSongs = computed(() => {
  const query = normalizedSongQuery.value;
  const items = playlistSongs.value.filter((item) => {
    if (!query) return true;
    return [item.song.title, item.song.artistName, item.song.albumTitle]
      .filter(Boolean)
      .some((value) => value!.toLocaleLowerCase().includes(query));
  });
  if (songSort.value === "TITLE") {
    return [...items].sort((a, b) => a.song.title.localeCompare(b.song.title, "zh-CN"));
  }
  if (songSort.value === "ARTIST") {
    return [...items].sort((a, b) =>
      (a.song.artistName || "").localeCompare(b.song.artistName || "", "zh-CN")
    );
  }
  if (songSort.value === "NEWEST") {
    return [...items].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
  }
  return items;
});
const filteredSongs = computed(() => filteredPlaylistSongs.value.map((item) => item.song));
const canReorder = computed(() =>
  Boolean(
    playlist.value
      && canManagePlaylist(playlist.value, auth.user?.id)
      && playlistSongs.value.length > 1
      && !normalizedSongQuery.value
      && songSort.value === "CUSTOM"
  )
);
const creatorInitial = computed(() => (playlist.value?.creatorNickname || "M").trim().slice(0, 1).toUpperCase());
const availableCandidates = computed(() => {
  const existing = new Set(playlistSongs.value.map((item) => item.songId));
  return candidateSongs.value.filter((song) => !existing.has(song.id));
});

onMounted(loadPlaylist);

async function loadPlaylist() {
  loading.value = true;
  try {
    const detail = await playlistApi.detail(Number(route.params.id));
    applyPlaylist(detail);
  } finally {
    loading.value = false;
  }
}

function applyPlaylist(detail: PlaylistDetail) {
  playlist.value = detail;
  playlistSongs.value = detail.songs;
  selectedSongIds.value = new Set();
  resetEditor();
}

function resetEditor() {
  if (!playlist.value) return;
  editForm.title = playlist.value.title;
  editForm.description = playlist.value.description || "";
  editForm.coverUrl = playlist.value.coverUrl || "";
  editForm.visibility = playlist.value.visibility;
  editForm.tagsText = playlist.value.tags.join(", ");
}

function toggleEditor() {
  editing.value = !editing.value;
  if (editing.value) resetEditor();
}

async function savePlaylist() {
  if (!playlist.value || editSaving.value) return;
  const tags = normalizeTags(editForm.tagsText);
  if (tags.length > 5) {
    ui.toast("标签最多 5 个");
    return;
  }
  if (tags.some((tag) => tag.length > 12)) {
    ui.toast("每个标签最多 12 个字");
    return;
  }
  editSaving.value = true;
  try {
    const detail = await playlistApi.update(playlist.value.id, {
      title: editForm.title,
      description: editForm.description,
      coverUrl: editForm.coverUrl,
      visibility: editForm.visibility,
      tags
    });
    applyPlaylist(detail);
    editing.value = false;
    ui.toast("歌单资料已更新");
  } finally {
    editSaving.value = false;
  }
}

async function uploadCover(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  coverUploading.value = true;
  try {
    const upload = await uploadApi.image(file, "PLAYLIST_COVER");
    editForm.coverUrl = upload.url;
    ui.toast("封面已上传，保存后生效");
  } finally {
    coverUploading.value = false;
    input.value = "";
  }
}

async function toggleFavorite() {
  if (!playlist.value || favoriteSaving.value) return;
  if (!auth.isAuthenticated) {
    await router.push({ name: "login", query: { redirect: route.fullPath } });
    return;
  }
  favoriteSaving.value = true;
  const wasFavorited = playlist.value.favorited;
  try {
    if (wasFavorited) {
      await favoriteApi.remove("PLAYLIST", playlist.value.id);
    } else {
      await favoriteApi.add("PLAYLIST", playlist.value.id);
    }
    playlist.value = applyPlaylistFavorite(playlist.value, !wasFavorited);
    ui.toast(wasFavorited ? "已取消收藏" : "已收藏歌单");
  } finally {
    favoriteSaving.value = false;
  }
}

async function sharePlaylist() {
  const url = window.location.href;
  try {
    if (navigator.share) {
      await navigator.share({ title: playlist.value?.title || "MeloSpace 歌单", url });
      return;
    }
    await navigator.clipboard.writeText(url);
    ui.toast("歌单链接已复制");
  } catch (error) {
    if ((error as DOMException).name !== "AbortError") {
      ui.toast("分享失败，请复制浏览器地址");
    }
  }
}

async function playAll() {
  if (!playlist.value || !filteredSongs.value.length) return;
  const played = await player.playSong(filteredSongs.value[0], filteredSongs.value);
  if (played) recordPlaylistPlay();
}

async function shufflePlay() {
  if (!playlist.value || !filteredSongs.value.length) return;
  const shuffled = [...filteredSongs.value];
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1));
    [shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
  }
  player.setPlayMode("shuffle");
  const played = await player.playSong(shuffled[0], shuffled);
  if (played) recordPlaylistPlay();
}

function recordPlaylistPlay() {
  if (!playlist.value) return;
  void playlistApi.recordPlay(playlist.value.id).then((detail) => {
    if (playlist.value) playlist.value.playCount = detail.playCount;
  });
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
  void player.playSong(song, filteredSongs.value);
}

async function openPlayer(song: Song) {
  const played = await player.playSong(song, filteredSongs.value);
  if (played) await router.push("/player");
}

async function toggleSongPicker() {
  addingSongs.value = !addingSongs.value;
  if (addingSongs.value && !candidateSongs.value.length) await searchCandidates();
}

async function searchCandidates() {
  candidateLoading.value = true;
  candidateSongIds.value = new Set();
  try {
    const result = await songApi.list({
      page: 1,
      size: 50,
      keyword: candidateKeyword.value || undefined,
      sort: "playsDesc"
    });
    candidateSongs.value = result.items;
  } finally {
    candidateLoading.value = false;
  }
}

function toggleCandidate(songId: number) {
  candidateSongIds.value = toggledSet(candidateSongIds.value, songId);
}

function toggleSelectedSong(songId: number) {
  selectedSongIds.value = toggledSet(selectedSongIds.value, songId);
}

async function addSelectedSongs() {
  if (!playlist.value || !candidateSongIds.value.size || batchAdding.value) return;
  batchAdding.value = true;
  try {
    const detail = await playlistApi.addSongs(playlist.value.id, [...candidateSongIds.value]);
    applyPlaylist(detail);
    candidateSongIds.value = new Set();
    ui.toast("歌曲已批量添加");
  } finally {
    batchAdding.value = false;
  }
}

async function removeSelectedSongs() {
  if (!playlist.value || !selectedSongIds.value.size || batchRemoving.value) return;
  if (!window.confirm(`确定从歌单移除选中的 ${selectedSongIds.value.size} 首歌曲吗？`)) return;
  batchRemoving.value = true;
  try {
    const detail = await playlistApi.removeSongs(playlist.value.id, [...selectedSongIds.value]);
    applyPlaylist(detail);
    ui.toast("所选歌曲已移除");
  } finally {
    batchRemoving.value = false;
  }
}

async function removeOneSong(songId: number) {
  if (!playlist.value || !window.confirm("确定从歌单中移除这首歌曲吗？")) return;
  const detail = await playlistApi.removeSong(playlist.value.id, songId);
  applyPlaylist(detail);
  ui.toast("歌曲已移除");
}

function startDrag(event: DragEvent, item: PlaylistSong) {
  if (!canReorder.value || savingOrder.value) {
    event.preventDefault();
    return;
  }
  draggedSongId.value = item.songId;
  dragOverSongId.value = item.songId;
  dragSnapshot.value = [...playlistSongs.value];
  dropHandled.value = false;
  event.dataTransfer?.setData("text/plain", String(item.songId));
  if (event.dataTransfer) event.dataTransfer.effectAllowed = "move";
}

function moveDraggedSong(target: PlaylistSong) {
  if (!canReorder.value || !draggedSongId.value || draggedSongId.value === target.songId || savingOrder.value) return;
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
  if (dragSnapshot.value.length) playlistSongs.value = [...dragSnapshot.value];
  resetDragState();
}

async function moveSongByButton(songId: number, direction: -1 | 1) {
  if (!canReorder.value || savingOrder.value) return;
  const index = playlistSongs.value.findIndex((item) => item.songId === songId);
  const nextIndex = index + direction;
  if (index < 0 || nextIndex < 0 || nextIndex >= playlistSongs.value.length) return;
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
    const detail = await playlistApi.reorder(playlist.value.id, playlistSongs.value.map((item) => item.songId));
    applyPlaylist(detail);
    ui.toast("歌单顺序已保存");
  } catch {
    playlistSongs.value = previousItems;
    ui.toast("排序保存失败，已恢复原顺序");
  } finally {
    savingOrder.value = false;
  }
}

function normalizeTags(value: string) {
  return [...new Set(value.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean))];
}

function toggledSet(values: Set<number>, value: number) {
  const next = new Set(values);
  if (next.has(value)) next.delete(value);
  else next.add(value);
  return next;
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

function compactNumber(value: number) {
  return new Intl.NumberFormat("zh-CN", { notation: "compact", maximumFractionDigits: 1 }).format(value || 0);
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "最近"
    : new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "short", day: "numeric" }).format(date);
}
</script>

<style scoped>
.playlist-community-page {
  display: grid;
  gap: 34px;
  min-width: 0;
}

.playlist-community-hero {
  display: grid;
  align-items: end;
  gap: clamp(22px, 4vw, 44px);
  grid-template-columns: minmax(190px, 270px) minmax(0, 1fr);
}

.playlist-community-cover {
  max-width: 270px;
  justify-self: start;
}

.playlist-community-copy {
  min-width: 0;
}

.playlist-community-copy .page-title {
  overflow-wrap: anywhere;
}

.playlist-description {
  max-width: 720px;
  white-space: pre-line;
  overflow-wrap: anywhere;
}

.playlist-creator {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  gap: 9px;
  margin: 5px 0;
  color: inherit;
  text-decoration: none;
}

.playlist-creator:hover strong,
.playlist-creator:focus-visible strong {
  color: var(--brand);
}

.playlist-creator-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: linear-gradient(145deg, var(--brand-soft), #fff);
  color: var(--brand);
  font-size: 13px;
}

.playlist-creator-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.playlist-tags,
.playlist-meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.playlist-tags {
  margin: 8px 0;
}

.playlist-tags span {
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
  font-weight: 680;
}

.playlist-meta-line {
  font-size: 13px;
}

.playlist-meta-line span:not(:last-child)::after {
  margin-left: 12px;
  content: "·";
  color: #b5b5ba;
}

.playlist-primary-actions button,
.playlist-panel-head > button,
.playlist-editor-actions button {
  min-height: 44px;
}

.playlist-primary-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
}

.playlist-primary-actions .is-active {
  border-color: color-mix(in srgb, var(--brand) 34%, transparent);
  background: var(--brand-soft);
  color: var(--brand);
}

.glass-panel {
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.66);
  box-shadow: 0 18px 52px rgba(18, 18, 24, 0.08);
  backdrop-filter: blur(24px) saturate(145%);
  -webkit-backdrop-filter: blur(24px) saturate(145%);
}

.playlist-editor,
.playlist-song-picker {
  padding: clamp(18px, 3vw, 28px);
}

.playlist-panel-head {
  align-items: center;
  margin-bottom: 18px;
}

.playlist-panel-head h2,
.playlist-panel-head h3,
.playlist-panel-head p {
  margin: 0;
}

.playlist-icon-button {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 50%;
  background: rgba(242, 242, 246, 0.9);
  color: #4f4f56;
}

.playlist-editor-grid {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.playlist-editor-grid label,
.playlist-cover-field {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
}

.playlist-editor-grid label > span,
.playlist-cover-field > span,
.playlist-sort-field > span {
  color: #52525a;
  font-size: 13px;
  font-weight: 680;
}

.playlist-editor-grid input,
.playlist-editor-grid select,
.playlist-editor-grid textarea,
.playlist-cover-controls > input,
.playlist-song-tools input,
.playlist-song-tools select,
.playlist-picker-search input {
  width: 100%;
  min-height: 44px;
  border: 1px solid rgba(209, 209, 216, 0.9);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.86);
  color: inherit;
}

.playlist-editor-grid textarea {
  resize: vertical;
}

.playlist-editor-grid small {
  justify-self: end;
  color: var(--muted);
}

.playlist-editor-wide {
  grid-column: 1 / -1;
}

.playlist-cover-controls {
  display: grid;
  gap: 10px;
  grid-template-columns: max-content minmax(0, 1fr);
}

.playlist-file-button {
  display: inline-flex !important;
  align-items: center;
  min-height: 44px;
  gap: 7px !important;
  cursor: pointer;
}

.playlist-file-button input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.playlist-editor-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.playlist-song-section {
  min-width: 0;
}

.playlist-song-tools {
  display: grid;
  align-items: end;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px;
  grid-template-columns: minmax(200px, 1fr) minmax(150px, 210px) max-content;
}

.playlist-search-field {
  display: grid;
  align-items: center;
  min-width: 0;
  grid-template-columns: 24px minmax(0, 1fr) 40px;
}

.playlist-search-field > svg {
  position: relative;
  z-index: 1;
  margin-right: -34px;
  margin-left: 12px;
  color: var(--muted);
  pointer-events: none;
}

.playlist-search-field input {
  grid-column: 1 / -1;
  grid-row: 1;
  padding-right: 42px;
  padding-left: 42px;
}

.playlist-search-field button {
  z-index: 1;
  display: grid;
  width: 40px;
  height: 40px;
  margin-left: -42px;
  place-items: center;
  border-radius: 10px;
  color: var(--muted);
}

.playlist-sort-field {
  display: grid;
  gap: 6px;
}

.playlist-danger-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  gap: 7px;
  border-radius: 999px;
  padding: 0 16px;
  background: rgba(184, 32, 50, 0.09);
  color: #b31d31;
  font-weight: 690;
}

.playlist-picker-search {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) max-content;
}

.playlist-picker-search label {
  display: grid;
  align-items: center;
  grid-template-columns: 40px minmax(0, 1fr);
}

.playlist-picker-search svg {
  z-index: 1;
  grid-column: 1;
  grid-row: 1;
  margin-left: 13px;
  color: var(--muted);
  pointer-events: none;
}

.playlist-picker-search input {
  grid-column: 1 / -1;
  grid-row: 1;
  padding-left: 42px;
}

.playlist-picker-status {
  padding: 28px 0;
  color: var(--muted);
  text-align: center;
}

.playlist-candidate-list {
  display: grid;
  max-height: 430px;
  gap: 6px;
  margin-top: 16px;
  overflow: auto;
}

.playlist-candidate-row {
  display: grid;
  align-items: center;
  min-height: 58px;
  gap: 10px;
  border-radius: 13px;
  padding: 7px 10px;
  cursor: pointer;
  grid-template-columns: 24px 44px minmax(0, 1fr);
}

.playlist-candidate-row:hover {
  background: rgba(242, 242, 246, 0.9);
}

.playlist-candidate-row input,
.playlist-select-song input {
  width: 18px;
  height: 18px;
  accent-color: var(--brand);
}

.playlist-candidate-row img,
.playlist-candidate-cover {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 9px;
  background: #eeeef2;
  object-fit: cover;
}

.playlist-candidate-row > span:last-child {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.playlist-candidate-row strong,
.playlist-candidate-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.playlist-candidate-row small {
  color: var(--muted);
}

.playlist-manage-row {
  grid-template-columns: 32px 30px minmax(0, 1fr) max-content;
}

.playlist-select-song {
  display: grid;
  width: 32px;
  height: 44px;
  place-items: center;
  cursor: pointer;
}

.playlist-drag-grip.is-disabled {
  cursor: default;
  opacity: 0.35;
}

.playlist-sort-actions button {
  width: 44px;
  height: 44px;
}

.playlist-private-note {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
}

.playlist-private-note p {
  margin: 3px 0 0;
  color: var(--muted);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .glass-panel {
    background: #fff;
  }
}

@media (max-width: 820px) {
  .playlist-community-page {
    gap: 26px;
  }

  .playlist-community-hero {
    align-items: center;
    grid-template-columns: minmax(130px, 190px) minmax(0, 1fr);
  }

  .playlist-song-tools {
    grid-template-columns: minmax(0, 1fr) minmax(145px, 190px);
  }

  .playlist-danger-button {
    grid-column: 1 / -1;
    justify-self: start;
  }

  .playlist-manage-row {
    grid-template-columns: 32px minmax(0, 1fr) max-content;
  }

  .playlist-drag-grip {
    display: none;
  }

  .playlist-sort-actions {
    display: grid;
    grid-template-columns: repeat(3, 44px);
  }
}

@media (max-width: 620px) {
  .playlist-community-hero {
    align-items: start;
    grid-template-columns: minmax(104px, 34vw) minmax(0, 1fr);
  }

  .playlist-community-cover {
    max-width: 150px;
    border-radius: 18px;
  }

  .playlist-community-copy .page-title {
    margin-bottom: 8px;
    font-size: clamp(26px, 8vw, 36px);
  }

  .playlist-description,
  .playlist-tags,
  .playlist-meta-line,
  .playlist-primary-actions {
    grid-column: 1 / -1;
  }

  .playlist-community-copy {
    display: contents;
  }

  .playlist-community-copy > .feature-label,
  .playlist-community-copy > .page-title,
  .playlist-community-copy > .playlist-creator {
    grid-column: 2;
  }

  .playlist-community-copy > .feature-label {
    align-self: end;
    margin: 4px 0 0;
  }

  .playlist-community-copy > .page-title {
    align-self: center;
  }

  .playlist-community-copy > .playlist-creator {
    align-self: start;
  }

  .playlist-description {
    margin-top: 2px;
  }

  .playlist-primary-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .playlist-primary-actions button {
    width: 100%;
  }

  .playlist-editor-grid,
  .playlist-cover-controls,
  .playlist-song-tools,
  .playlist-picker-search {
    grid-template-columns: minmax(0, 1fr);
  }

  .playlist-editor-wide,
  .playlist-danger-button {
    grid-column: auto;
  }

  .playlist-song-section > .playlist-panel-head {
    align-items: flex-start;
    gap: 12px;
  }

  .playlist-song-section > .playlist-panel-head > button {
    flex: 0 0 auto;
  }

  .playlist-manage-row {
    gap: 4px;
    padding: 7px 4px;
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .playlist-manage-row .playlist-sort-actions {
    grid-column: 1 / -1;
    justify-content: end;
  }

  .playlist-select-song {
    width: 28px;
  }

  .playlist-sort-actions button {
    width: 44px;
    height: 44px;
  }

  .playlist-editor-actions {
    flex-wrap: wrap;
  }
}

@media (max-width: 390px) {
  .playlist-community-hero {
    gap: 16px 14px;
  }

  .playlist-meta-line {
    gap: 5px 8px;
  }

  .playlist-meta-line span:not(:last-child)::after {
    margin-left: 8px;
  }

  .playlist-primary-actions {
    grid-template-columns: minmax(0, 1fr);
  }

  .playlist-editor,
  .playlist-song-picker {
    padding: 16px;
  }
}
</style>
