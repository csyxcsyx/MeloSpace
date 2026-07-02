<template>
  <section>
    <PageToolbar />
    <EmptyState v-if="loading">正在加载歌手...</EmptyState>
    <template v-else-if="artist">
      <header class="catalog-hero artist-catalog-hero">
        <div class="catalog-cover artist-cover">
          <img v-if="artistImage" :src="resolveMediaUrl(artistImage)" alt="" />
          <UserRound v-else :size="48" />
        </div>
        <div class="catalog-copy">
          <p class="feature-label">歌手</p>
          <h1>{{ artist.name }}</h1>
          <p>{{ artist.bio || "MeloSpace 曲库歌手" }}</p>
          <p class="catalog-meta">{{ songs.length }} 首歌 · {{ albums.length }} 张专辑</p>
          <div class="detail-actions">
            <button class="primary-action" type="button" :disabled="!songs.length" @click="playAll">
              <Play :size="17" fill="currentColor" />
              播放热门
            </button>
          </div>
        </div>
      </header>

      <section v-if="albums.length" class="artist-album-strip">
        <RouterLink v-for="album in albums.slice(0, 6)" :key="album.id" class="artist-album-link" :to="`/albums/${album.id}`">
          <img v-if="album.coverUrl" :src="resolveMediaUrl(album.coverUrl)" alt="" />
          <Disc3 v-else :size="26" />
          <span>
            <strong>{{ album.title }}</strong>
            <small>{{ album.releaseDate || "专辑" }}</small>
          </span>
        </RouterLink>
      </section>

      <section>
        <div class="section-head">
          <h2>热门歌曲</h2>
        </div>
        <SongColumnList
          v-if="songs.length"
          :songs="songs"
          :column-count="2"
          @toggle-play="toggleSongPlayback"
          @open-player="openPlayer"
        />
        <EmptyState v-else>这位歌手还没有歌曲。</EmptyState>
      </section>
    </template>
    <EmptyState v-else>没有找到这位歌手。</EmptyState>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { Disc3, Play, UserRound } from "lucide-vue-next";
import { albumApi, artistApi, songApi } from "@/api";
import type { Album, Artist, Song } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import PageToolbar from "@/components/PageToolbar.vue";
import SongColumnList from "@/components/SongColumnList.vue";
import { usePlayerStore } from "@/stores/player";
import { resolveMediaUrl } from "@/utils/format";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
const loading = ref(true);
const artist = ref<Artist | null>(null);
const albums = ref<Album[]>([]);
const songs = ref<Song[]>([]);

const artistImage = computed(() => {
  return artist.value?.avatarUrl
    || songs.value.find((song) => song.coverUrl)?.coverUrl
    || albums.value.find((album) => album.coverUrl)?.coverUrl
    || "";
});

onMounted(loadArtist);

watch(() => route.params.id, loadArtist);

async function loadArtist() {
  const artistId = Number(route.params.id);
  if (!Number.isFinite(artistId)) {
    artist.value = null;
    albums.value = [];
    songs.value = [];
    loading.value = false;
    return;
  }

  loading.value = true;
  try {
    const [artistList, albumList, songPage] = await Promise.all([
      artistApi.list(),
      albumApi.list({ artistId }),
      songApi.list({ page: 1, size: 100, artistId })
    ]);
    artist.value = artistList.find((item) => item.id === artistId) ?? null;
    albums.value = albumList;
    songs.value = songPage.items;
  } finally {
    loading.value = false;
  }
}

function playAll() {
  if (!songs.value.length) return;
  player.playSong(songs.value[0], songs.value);
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
</script>
