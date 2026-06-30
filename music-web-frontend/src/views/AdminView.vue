<template>
  <section>
    <h1 class="page-title">后台管理</h1>

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
          <h2>新建歌曲</h2>
          <span class="chevron">›</span>
        </div>

        <form class="admin-form" @submit.prevent="createSong">
          <label>
            歌曲名
            <input v-model.trim="songForm.title" required />
          </label>
          <label>
            歌手
            <select v-model.number="songForm.artistId" required>
              <option :value="0">选择歌手</option>
              <option v-for="artist in artists" :key="artist.id" :value="artist.id">{{ artist.name }}</option>
            </select>
          </label>
          <label>
            专辑
            <select v-model.number="songForm.albumId">
              <option :value="0">无专辑</option>
              <option v-for="album in albums" :key="album.id" :value="album.id">{{ album.title }}</option>
            </select>
          </label>
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
            <input v-model.trim="songForm.lyricUrl" placeholder="/media/lyrics/demo.lrc" />
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
          <button class="primary-action" type="submit">创建歌曲</button>
        </form>
      </div>

      <div class="compact-panel">
        <div class="section-head">
          <h2>上传资源</h2>
          <span class="chevron">›</span>
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
          <button class="secondary-action" type="submit">上传并填入表单</button>
          <p v-if="uploadedUrl" class="muted-line">已上传：{{ uploadedUrl }}</p>
        </form>

        <div class="section-head">
          <h2>新建歌手</h2>
          <span class="chevron">›</span>
        </div>
        <form class="inline-form" @submit.prevent="createArtist">
          <input v-model.trim="artistName" placeholder="歌手名" />
          <button type="submit">创建</button>
        </form>
      </div>
    </section>

    <section class="compact-panel">
      <div class="section-head">
        <h2>歌曲列表</h2>
        <span class="chevron">›</span>
      </div>
      <div class="admin-song-list">
        <div v-for="song in songs" :key="song.id" class="admin-song-row">
          <div>
            <strong>{{ song.title }}</strong>
            <span>{{ song.artistName || "未知歌手" }} · {{ song.audioUrl }}</span>
          </div>
          <button class="status-pill" type="button" @click="toggleStatus(song)">
            {{ song.status === 1 ? "上架中" : "已下架" }}
          </button>
        </div>
        <EmptyState v-if="!songs.length">暂无歌曲。</EmptyState>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { adminApi, albumApi, artistApi } from "@/api";
import type { Album, Artist, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
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
  mood: "热烈",
  status: 1
});

onMounted(loadAdmin);

async function loadAdmin() {
  const [stats, songPage, artistPage, albumPage] = await Promise.all([
    adminApi.dashboard(),
    adminApi.songs({ page: 1, size: 50 }),
    artistApi.list({ page: 1, size: 100 }),
    albumApi.list({ page: 1, size: 100 })
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
  artistName.value = "";
  ui.toast("歌手已创建");
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] ?? null;
}

async function uploadFile() {
  if (!selectedFile.value) return;
  const file = await adminApi.upload(selectedFile.value, uploadType.value);
  uploadedUrl.value = file.url;
  if (file.fileType === "AUDIO") songForm.audioUrl = file.url;
  if (file.fileType === "COVER") songForm.coverUrl = file.url;
  if (file.fileType === "LYRIC") songForm.lyricUrl = file.url;
  ui.toast("资源上传成功");
}

async function createSong() {
  if (!songForm.artistId || !songForm.audioUrl) return;
  await adminApi.createSong({
    title: songForm.title,
    artistId: songForm.artistId,
    albumId: songForm.albumId || null,
    audioUrl: songForm.audioUrl,
    coverUrl: songForm.coverUrl || null,
    lyricUrl: songForm.lyricUrl || null,
    durationSeconds: songForm.durationSeconds,
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
  await loadAdmin();
}

async function toggleStatus(song: Song) {
  const updated = await adminApi.updateSongStatus(song.id, song.status === 1 ? 0 : 1);
  songs.value = songs.value.map((item) => (item.id === updated.id ? updated : item));
}
</script>
