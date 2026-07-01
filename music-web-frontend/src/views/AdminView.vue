<template>
  <section>
    <PageToolbar />
    <div class="admin-title-row">
      <h1 class="page-title">后台管理</h1>
      <button class="secondary-action" type="button" @click="loadAdmin">
        <RefreshCw :size="16" />
        <span>刷新</span>
      </button>
    </div>

    <section class="metrics">
      <div class="metric">
        <strong>{{ dashboard.users ?? 0 }}</strong>
        <span>用户</span>
      </div>
      <div class="metric">
        <strong>{{ dashboard.songs ?? 0 }}</strong>
        <span>歌曲</span>
      </div>
      <div class="metric">
        <strong>{{ dashboard.playlists ?? 0 }}</strong>
        <span>歌单</span>
      </div>
      <div class="metric">
        <strong>{{ dashboard.comments ?? 0 }}</strong>
        <span>评论</span>
      </div>
    </section>

    <section class="admin-layout">
      <div class="compact-panel">
        <div class="section-head">
          <Music2 :size="18" />
          <h2>{{ editingSongId ? "编辑歌曲" : "新增歌曲" }}</h2>
        </div>

        <form class="admin-form" @submit.prevent="createSong">
          <label>
            歌曲名
            <input v-model.trim="songForm.title" required />
          </label>
          <div class="form-row">
            <label>
              歌手
              <select v-model.number="songForm.artistId" required>
                <option :value="0">选择歌手</option>
                <option v-for="artist in artists" :key="artist.id" :value="artist.id">{{ artist.name }}</option>
              </select>
            </label>
            <label>
              专辑
              <select v-model.number="songForm.albumId" required>
                <option :value="0">选择专辑</option>
                <option v-for="album in albums" :key="album.id" :value="album.id">
                  {{ album.title }}{{ album.artistName ? ` - ${album.artistName}` : "" }}
                </option>
              </select>
            </label>
            <label>
              时长（秒）
              <input v-model.number="songForm.durationSeconds" min="0" type="number" />
            </label>
          </div>

          <div class="file-picker-grid">
            <label class="file-picker">
              <span>音频文件</span>
              <input
                :key="songFileInputKey"
                accept="audio/*,.flac"
                :required="!editingSongId && !songForm.audioUrl"
                type="file"
                @change="onSongAudioChange"
              />
              <small>{{ fileStatus(songAudioFile, songForm.audioUrl, "请选择 MP3、FLAC、WAV 等音频") }}</small>
            </label>
            <label class="file-picker">
              <span>歌词文件</span>
              <input
                :key="songFileInputKey + '-lyric'"
                accept=".lrc,.txt,text/plain"
                type="file"
                @change="onSongLyricChange"
              />
              <small>{{ fileStatus(songLyricFile, songForm.lyricUrl, "可选，未选时自动用 LDDC 匹配") }}</small>
            </label>
          </div>

          <div class="file-actions">
            <button class="secondary-action" type="button" :disabled="lyricMatching" @click="matchLyrics">
              <Wand2 :size="16" />
              <span>{{ lyricMatching ? "匹配中..." : "用 LDDC 匹配歌词" }}</span>
            </button>
            <span class="form-help">音频会先上传到网站媒体库，再用于本地 LDDC 匹配；歌曲封面自动使用所选专辑图。</span>
          </div>

          <div class="form-row">
            <label>
              语言
              <input v-model.trim="songForm.language" />
            </label>
            <label>
              风格
              <input v-model.trim="songForm.genre" />
            </label>
            <label>
              情绪
              <input v-model.trim="songForm.mood" />
            </label>
          </div>
          <button class="primary-action" type="submit" :disabled="savingSong">
            <Plus :size="16" />
            <span>{{ savingSong ? "保存中..." : editingSongId ? "保存歌曲" : "创建歌曲" }}</span>
          </button>
          <button v-if="editingSongId" class="secondary-action" type="button" @click="cancelSongEdit">取消编辑</button>
        </form>
      </div>

      <div class="compact-panel">
        <div class="section-head">
          <Mic2 :size="18" />
          <h2>新增歌手</h2>
        </div>
        <form class="inline-form" @submit.prevent="createArtist">
          <input v-model.trim="artistName" placeholder="歌手名" />
          <button type="submit">
            <Plus :size="15" />
            <span>创建</span>
          </button>
        </form>

        <div class="section-head">
          <Disc3 :size="18" />
          <h2>{{ editingAlbumId ? "编辑专辑" : "新增专辑" }}</h2>
        </div>
        <form class="admin-form" @submit.prevent="createAlbum">
          <label>
            专辑名
            <input v-model.trim="albumForm.title" required />
          </label>
          <label>
            歌手
            <select v-model.number="albumForm.artistId" required>
              <option :value="0">选择歌手</option>
              <option v-for="artist in artists" :key="artist.id" :value="artist.id">{{ artist.name }}</option>
            </select>
          </label>
          <label class="file-picker">
            <span>专辑图</span>
            <input
              :key="albumFileInputKey"
              accept="image/*"
              :required="!editingAlbumId && !albumForm.coverUrl"
              type="file"
              @change="onAlbumCoverChange"
            />
            <small>{{ fileStatus(albumCoverFile, albumForm.coverUrl, "请选择 JPG、PNG、WebP 等图片") }}</small>
          </label>
          <label>
            发行日期
            <input v-model.trim="albumForm.releaseDate" type="date" />
          </label>
          <button class="secondary-action" type="submit" :disabled="savingAlbum">
            <Plus :size="16" />
            <span>{{ savingAlbum ? "保存中..." : editingAlbumId ? "保存专辑" : "创建专辑" }}</span>
          </button>
          <button v-if="editingAlbumId" class="secondary-action" type="button" @click="cancelAlbumEdit">取消编辑</button>
        </form>
      </div>
    </section>

    <section class="compact-panel admin-list-section">
      <div class="section-head">
        <ListMusic :size="18" />
        <h2>歌曲管理</h2>
      </div>
      <div class="admin-song-list">
        <div v-for="song in songs" :key="song.id" class="admin-song-row">
          <div>
            <strong>{{ song.title }}</strong>
            <span>{{ song.artistName || "未知歌手" }} · {{ song.albumTitle || "未绑定专辑" }}</span>
            <small>{{ song.lyricUrl ? "歌词已配置" : "暂无歌词" }} · 封面跟随专辑图</small>
          </div>
          <div class="admin-row-actions">
            <button class="secondary-action mini-action" type="button" @click="editSong(song)">
              <Pencil :size="15" />
              <span>编辑</span>
            </button>
            <button class="status-pill" type="button" @click="toggleStatus(song)">
              {{ song.status === 1 ? "上架中" : "已下架" }}
            </button>
            <button class="danger-action" type="button" @click="deleteSong(song)">
              <Trash2 :size="15" />
              <span>删除</span>
            </button>
          </div>
        </div>
        <EmptyState v-if="!songs.length">暂无歌曲。</EmptyState>
      </div>
    </section>

    <section class="compact-panel admin-list-section">
      <div class="section-head">
        <Disc3 :size="18" />
        <h2>专辑管理</h2>
      </div>
      <div class="admin-album-list">
        <div v-for="album in albums" :key="album.id" class="admin-song-row">
          <div>
            <strong>{{ album.title }}</strong>
            <span>{{ album.artistName || "未知歌手" }}</span>
            <small>{{ album.coverUrl ? "专辑图已上传" : "暂无专辑图" }}</small>
          </div>
          <div class="admin-row-actions">
            <button class="secondary-action mini-action" type="button" @click="editAlbum(album)">
              <Pencil :size="15" />
              <span>编辑</span>
            </button>
            <button class="secondary-action mini-action" type="button" @click="bindAlbum(album)">
              <Disc3 :size="15" />
              <span>绑定</span>
            </button>
            <button class="danger-action" type="button" @click="deleteAlbum(album)">
              <Trash2 :size="15" />
              <span>删除</span>
            </button>
          </div>
        </div>
        <EmptyState v-if="!albums.length">暂无专辑。</EmptyState>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { Disc3, ListMusic, Mic2, Music2, Pencil, Plus, RefreshCw, Trash2, Wand2 } from "lucide-vue-next";
import { adminApi, albumApi, artistApi } from "@/api";
import type { Album, Artist, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import { useUiStore } from "@/stores/ui";

const ui = useUiStore();
const dashboard = reactive<Record<string, number>>({});
const songs = ref<Song[]>([]);
const artists = ref<Artist[]>([]);
const albums = ref<Album[]>([]);
const artistName = ref("");
const lyricMatching = ref(false);
const savingSong = ref(false);
const savingAlbum = ref(false);
const editingSongId = ref<number | null>(null);
const editingAlbumId = ref<number | null>(null);
const songFileInputKey = ref(0);
const albumFileInputKey = ref(0);

const songAudioFile = ref<File | null>(null);
const songLyricFile = ref<File | null>(null);
const albumCoverFile = ref<File | null>(null);

const songForm = reactive({
  title: "",
  artistId: 0,
  albumId: 0,
  audioUrl: "",
  lyricUrl: "",
  durationSeconds: 0,
  language: "中文",
  genre: "Pop",
  mood: "",
  status: 1
});

const albumForm = reactive({
  title: "",
  artistId: 0,
  coverUrl: "",
  releaseDate: ""
});

const selectedSongArtist = computed(() => artists.value.find((item) => item.id === songForm.artistId) ?? null);
const selectedSongAlbum = computed(() => albums.value.find((item) => item.id === songForm.albumId) ?? null);

watch(
  () => songForm.artistId,
  (artistId) => {
    if (!artistId) return;
    albumForm.artistId = artistId;
  }
);

onMounted(loadAdmin);

async function loadAdmin() {
  const [stats, songPage, artistPage, albumPage] = await Promise.all([
    adminApi.dashboard(),
    adminApi.songs({ page: 1, size: 100 }),
    artistApi.list({ page: 1, size: 200 }),
    albumApi.list({ page: 1, size: 200 })
  ]);
  Object.assign(dashboard, stats);
  songs.value = songPage.items;
  artists.value = artistPage;
  albums.value = albumPage;
}

async function createArtist() {
  if (!artistName.value) return;
  const artist = await adminApi.createArtist({ name: artistName.value });
  artists.value.unshift(artist);
  songForm.artistId = artist.id;
  albumForm.artistId = artist.id;
  artistName.value = "";
  ui.toast("歌手已创建");
}

async function createAlbum() {
  if (!albumForm.title || !albumForm.artistId) {
    ui.toast("请填写专辑名并选择歌手");
    return;
  }
  if (!albumCoverFile.value && !albumForm.coverUrl) {
    ui.toast("请选择专辑图文件");
    return;
  }

  savingAlbum.value = true;
  try {
    const coverUrl = await ensureAlbumCoverUrl();
    const isEditing = Boolean(editingAlbumId.value);
    const payload = {
      title: albumForm.title,
      artistId: albumForm.artistId,
      coverUrl,
      releaseDate: albumForm.releaseDate || undefined
    };
    const album = editingAlbumId.value
      ? await adminApi.updateAlbum(editingAlbumId.value, payload)
      : await adminApi.createAlbum(payload);
    if (isEditing) {
      albums.value = albums.value.map((item) => (item.id === album.id ? album : item));
    } else {
      albums.value.unshift(album);
      songForm.albumId = album.id;
    }
    resetAlbumForm();
    ui.toast(isEditing ? "专辑已更新" : "专辑已创建");
  } finally {
    savingAlbum.value = false;
  }
}

function onSongAudioChange(event: Event) {
  songAudioFile.value = getInputFile(event);
  songForm.audioUrl = "";
  if (songAudioFile.value) {
    readAudioDuration(songAudioFile.value);
  }
}

function onSongLyricChange(event: Event) {
  songLyricFile.value = getInputFile(event);
  songForm.lyricUrl = "";
}

function onAlbumCoverChange(event: Event) {
  albumCoverFile.value = getInputFile(event);
  albumForm.coverUrl = "";
}

function getInputFile(event: Event) {
  const input = event.target as HTMLInputElement;
  return input.files?.[0] ?? null;
}

function fileStatus(file: File | null, uploadedUrl: string, emptyText: string) {
  if (uploadedUrl) return "已上传到网站媒体库";
  if (file) return file.name;
  return emptyText;
}

async function matchLyrics() {
  if (!validateSongBase()) return;

  lyricMatching.value = true;
  try {
    const audioUrl = await ensureSongAudioUrl();
    songForm.lyricUrl = await importLddcLyrics(audioUrl);
    ui.toast("歌词已匹配并写入表单");
  } finally {
    lyricMatching.value = false;
  }
}

async function createSong() {
  if (!validateSongBase()) return;
  if (!songAudioFile.value && !songForm.audioUrl) {
    ui.toast("请选择歌曲音频文件");
    return;
  }

  savingSong.value = true;
  try {
    const audioUrl = await ensureSongAudioUrl();
    const coverUrl = selectedSongAlbum.value?.coverUrl ?? "";
    const lyricUrl = await ensureSongLyricUrl(audioUrl);
    const isEditing = Boolean(editingSongId.value);

    const payload = {
      title: songForm.title,
      artistId: songForm.artistId,
      albumId: songForm.albumId,
      audioUrl,
      coverUrl: coverUrl || null,
      lyricUrl: lyricUrl || null,
      durationSeconds: Number(songForm.durationSeconds) || 0,
      language: songForm.language,
      genre: songForm.genre,
      mood: songForm.mood,
      status: songForm.status
    };
    if (isEditing && editingSongId.value) {
      await adminApi.updateSong(editingSongId.value, payload);
    } else {
      await adminApi.createSong(payload);
    }
    ui.toast(isEditing ? "歌曲已更新" : "歌曲已创建");
    resetSongForm();
    await loadAdmin();
  } finally {
    savingSong.value = false;
  }
}

function validateSongBase() {
  if (!songForm.title || !songForm.artistId || !songForm.albumId) {
    ui.toast("请填写歌曲名、歌手和专辑");
    return false;
  }
  if (!selectedSongArtist.value || !selectedSongAlbum.value) {
    ui.toast("请选择有效的歌手和专辑");
    return false;
  }
  return true;
}

async function ensureSongAudioUrl() {
  if (songForm.audioUrl) return songForm.audioUrl;
  if (!songAudioFile.value) {
    throw new Error("Missing song audio file");
  }
  const upload = await adminApi.upload(songAudioFile.value, "AUDIO");
  songForm.audioUrl = upload.url;
  return upload.url;
}

async function ensureSongLyricUrl(audioUrl: string) {
  if (songForm.lyricUrl) return songForm.lyricUrl;
  if (songLyricFile.value) {
    const upload = await adminApi.upload(songLyricFile.value, "LYRIC");
    songForm.lyricUrl = upload.url;
    return upload.url;
  }
  songForm.lyricUrl = await importLddcLyrics(audioUrl);
  return songForm.lyricUrl;
}

async function ensureAlbumCoverUrl() {
  if (albumForm.coverUrl) return albumForm.coverUrl;
  if (!albumCoverFile.value) {
    throw new Error("Missing album cover file");
  }
  const upload = await adminApi.upload(albumCoverFile.value, "COVER");
  albumForm.coverUrl = upload.url;
  return upload.url;
}

async function importLddcLyrics(audioUrl: string) {
  if (!selectedSongArtist.value || !selectedSongAlbum.value) {
    throw new Error("Missing artist or album");
  }
  const result = await adminApi.importLddcLyrics({
    title: songForm.title,
    artist: selectedSongArtist.value.name,
    album: selectedSongAlbum.value.title,
    audioUrl,
    durationSeconds: songForm.durationSeconds || undefined
  });
  return result.lyricUrl;
}

function readAudioDuration(file: File) {
  const audio = document.createElement("audio");
  const objectUrl = URL.createObjectURL(file);
  audio.preload = "metadata";
  audio.onloadedmetadata = () => {
    if (Number.isFinite(audio.duration) && audio.duration > 0) {
      songForm.durationSeconds = Math.round(audio.duration);
    }
    URL.revokeObjectURL(objectUrl);
  };
  audio.onerror = () => URL.revokeObjectURL(objectUrl);
  audio.src = objectUrl;
}

function resetSongForm() {
  editingSongId.value = null;
  songForm.title = "";
  songForm.artistId = 0;
  songForm.albumId = 0;
  songForm.audioUrl = "";
  songForm.lyricUrl = "";
  songForm.durationSeconds = 0;
  songForm.language = "中文";
  songForm.genre = "Pop";
  songForm.mood = "";
  songForm.status = 1;
  songAudioFile.value = null;
  songLyricFile.value = null;
  songFileInputKey.value += 1;
}

function resetAlbumForm() {
  editingAlbumId.value = null;
  albumForm.title = "";
  albumForm.artistId = 0;
  albumForm.coverUrl = "";
  albumForm.releaseDate = "";
  albumCoverFile.value = null;
  albumFileInputKey.value += 1;
}

function editSong(song: Song) {
  editingSongId.value = song.id;
  songForm.title = song.title;
  songForm.artistId = song.artistId;
  songForm.albumId = song.albumId ?? 0;
  songForm.audioUrl = song.audioUrl;
  songForm.lyricUrl = song.lyricUrl ?? "";
  songForm.durationSeconds = song.durationSeconds ?? 0;
  songForm.language = song.language ?? "";
  songForm.genre = song.genre ?? "";
  songForm.mood = song.mood ?? "";
  songForm.status = song.status;
  songAudioFile.value = null;
  songLyricFile.value = null;
  songFileInputKey.value += 1;
}

function cancelSongEdit() {
  resetSongForm();
}

function editAlbum(album: Album) {
  editingAlbumId.value = album.id;
  albumForm.title = album.title;
  albumForm.artistId = album.artistId;
  albumForm.coverUrl = album.coverUrl ?? "";
  albumForm.releaseDate = album.releaseDate ?? "";
  albumCoverFile.value = null;
  albumFileInputKey.value += 1;
}

function cancelAlbumEdit() {
  resetAlbumForm();
}

async function toggleStatus(song: Song) {
  const updated = await adminApi.updateSongStatus(song.id, song.status === 1 ? 0 : 1);
  songs.value = songs.value.map((item) => (item.id === updated.id ? updated : item));
}

async function deleteSong(song: Song) {
  if (!window.confirm(`确定删除歌曲「${song.title}」吗？`)) return;
  await adminApi.deleteSong(song.id);
  ui.toast("歌曲已删除");
  await loadAdmin();
}

function bindAlbum(album: Album) {
  songForm.albumId = album.id;
}

async function deleteAlbum(album: Album) {
  if (!window.confirm(`确定删除专辑「${album.title}」吗？`)) return;
  await adminApi.deleteAlbum(album.id);
  if (songForm.albumId === album.id) {
    songForm.albumId = 0;
  }
  ui.toast("专辑已删除");
  await loadAdmin();
}
</script>
