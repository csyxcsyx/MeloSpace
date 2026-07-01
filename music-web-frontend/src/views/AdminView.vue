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
          <h2>新增歌曲</h2>
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
                <option v-for="album in filteredAlbums" :key="album.id" :value="album.id">
                  {{ album.title }}{{ album.artistName ? ` - ${album.artistName}` : "" }}
                </option>
              </select>
            </label>
            <label>
              时长（秒）
              <input v-model.number="songForm.durationSeconds" min="0" type="number" />
            </label>
          </div>
          <label>
            音频 URL
            <input v-model.trim="songForm.audioUrl" required placeholder="/media/audio/demo.flac" />
          </label>
          <label>
            封面 URL
            <input v-model.trim="songForm.coverUrl" placeholder="/media/cover/demo.jpg" />
          </label>
          <label>
            歌词 URL
            <div class="field-with-action">
              <input v-model.trim="songForm.lyricUrl" placeholder="/media/lyrics/demo.lrc" />
              <button class="secondary-action" type="button" :disabled="lyricMatching" @click="matchLyrics">
                <Wand2 :size="16" />
                <span>{{ lyricMatching ? "匹配中..." : "匹配歌词" }}</span>
              </button>
            </div>
          </label>
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
          <button class="primary-action" type="submit">
            <Plus :size="16" />
            <span>创建歌曲</span>
          </button>
        </form>
      </div>

      <div class="compact-panel">
        <div class="section-head">
          <Upload :size="18" />
          <h2>上传资源</h2>
        </div>
        <form class="admin-form" @submit.prevent="uploadFile">
          <label>
            文件类型
            <select v-model="uploadType">
              <option value="AUDIO">音频 AUDIO</option>
              <option value="COVER">封面 COVER</option>
              <option value="LYRIC">歌词 LYRIC</option>
            </select>
          </label>
          <label>
            文件
            <input type="file" @change="onFileChange" />
          </label>
          <button class="secondary-action" type="submit">
            <Upload :size="16" />
            <span>上传</span>
          </button>
          <p v-if="uploadedUrl" class="muted-line url-chip">{{ uploadedUrl }}</p>
        </form>

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
          <h2>新增专辑</h2>
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
          <label>
            专辑图 URL
            <input v-model.trim="albumForm.coverUrl" placeholder="/media/cover/album.jpg" />
          </label>
          <label>
            发行日期
            <input v-model.trim="albumForm.releaseDate" type="date" />
          </label>
          <button class="secondary-action" type="submit">
            <Plus :size="16" />
            <span>创建专辑</span>
          </button>
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
            <small>{{ song.audioUrl }}</small>
          </div>
          <div class="admin-row-actions">
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
            <small>{{ album.coverUrl || "无专辑图" }}</small>
          </div>
          <div class="admin-row-actions">
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
import { Disc3, ListMusic, Mic2, Music2, Plus, RefreshCw, Trash2, Upload, Wand2 } from "lucide-vue-next";
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
const uploadType = ref<"AUDIO" | "COVER" | "LYRIC">("AUDIO");
const selectedFile = ref<File | null>(null);
const uploadedUrl = ref("");
const lyricMatching = ref(false);

const songForm = reactive({
  title: "",
  artistId: 0,
  albumId: 0,
  audioUrl: "",
  coverUrl: "",
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

const filteredAlbums = computed(() => {
  if (!songForm.artistId) return albums.value;
  return albums.value.filter((album) => album.artistId === songForm.artistId);
});

watch(
  () => songForm.artistId,
  (artistId) => {
    if (!artistId) return;
    albumForm.artistId = artistId;
    const selectedAlbum = albums.value.find((album) => album.id === songForm.albumId);
    if (selectedAlbum && selectedAlbum.artistId !== artistId) {
      songForm.albumId = 0;
    }
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
  const album = await adminApi.createAlbum({
    title: albumForm.title,
    artistId: albumForm.artistId,
    coverUrl: albumForm.coverUrl || undefined,
    releaseDate: albumForm.releaseDate || undefined
  });
  albums.value.unshift(album);
  songForm.artistId = album.artistId;
  songForm.albumId = album.id;
  if (album.coverUrl && !songForm.coverUrl) {
    songForm.coverUrl = album.coverUrl;
  }
  albumForm.title = "";
  albumForm.coverUrl = "";
  albumForm.releaseDate = "";
  ui.toast("专辑已创建");
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] ?? null;
}

async function uploadFile() {
  if (!selectedFile.value) {
    ui.toast("请选择文件");
    return;
  }
  const file = await adminApi.upload(selectedFile.value, uploadType.value);
  uploadedUrl.value = file.url;
  if (file.fileType === "AUDIO") songForm.audioUrl = file.url;
  if (file.fileType === "COVER") {
    songForm.coverUrl = file.url;
    albumForm.coverUrl = file.url;
  }
  if (file.fileType === "LYRIC") songForm.lyricUrl = file.url;
  ui.toast("资源上传成功");
}

async function matchLyrics() {
  const artist = artists.value.find((item) => item.id === songForm.artistId);
  const album = albums.value.find((item) => item.id === songForm.albumId);
  if (!songForm.title || !artist || !album || !songForm.audioUrl) {
    ui.toast("请先填写歌曲名、歌手、专辑和音频 URL");
    return;
  }

  lyricMatching.value = true;
  try {
    const result = await adminApi.importLddcLyrics({
      title: songForm.title,
      artist: artist.name,
      album: album.title,
      audioUrl: songForm.audioUrl,
      durationSeconds: songForm.durationSeconds || undefined
    });
    songForm.lyricUrl = result.lyricUrl;
    ui.toast(`歌词已匹配：${result.matchedTitle || songForm.title}`);
  } finally {
    lyricMatching.value = false;
  }
}

async function createSong() {
  if (!songForm.title || !songForm.artistId || !songForm.albumId || !songForm.audioUrl) {
    ui.toast("请填写歌曲名、歌手、专辑和音频 URL");
    return;
  }
  await adminApi.createSong({
    title: songForm.title,
    artistId: songForm.artistId,
    albumId: songForm.albumId,
    audioUrl: songForm.audioUrl,
    coverUrl: songForm.coverUrl || null,
    lyricUrl: songForm.lyricUrl || null,
    durationSeconds: Number(songForm.durationSeconds) || 0,
    language: songForm.language,
    genre: songForm.genre,
    mood: songForm.mood,
    status: songForm.status
  });
  ui.toast("歌曲已创建");
  songForm.title = "";
  songForm.audioUrl = "";
  songForm.coverUrl = "";
  songForm.lyricUrl = "";
  songForm.durationSeconds = 0;
  await loadAdmin();
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
  songForm.artistId = album.artistId;
  songForm.albumId = album.id;
  if (album.coverUrl && !songForm.coverUrl) {
    songForm.coverUrl = album.coverUrl;
  }
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
