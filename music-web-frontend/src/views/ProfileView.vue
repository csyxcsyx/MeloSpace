<template>
  <section class="profile-page profile-hub">
    <header class="page-header">
      <p class="feature-label">个人中心</p>
      <h1 class="page-title">我的音乐</h1>
    </header>

    <section class="profile-editor-card profile-summary-card" data-glass="regular" aria-labelledby="profile-name">
      <div class="profile-editor-avatar">
        <span>
          <img v-if="profileAvatarUrl" :src="resolveMediaUrl(profileAvatarUrl)" alt="" />
          <strong v-else>{{ profileInitial }}</strong>
        </span>
        <div>
          <h2 id="profile-name">{{ profileNickname || auth.user?.username }}</h2>
          <p>{{ profileBio || "在这里整理属于你的音乐宇宙。" }}</p>
        </div>
      </div>
      <div class="profile-summary-stats">
        <button type="button" @click="selectSection('playlists')">
          <strong>{{ playlistPage.total }}</strong><span>创建的歌单</span>
        </button>
        <button type="button" @click="selectSection('favorites')">
          <strong>{{ favoritePage.total }}</strong><span>收藏</span>
        </button>
        <button type="button" @click="selectSection('recent')">
          <strong>{{ recentPage.total }}</strong><span>最近播放</span>
        </button>
      </div>
    </section>

    <nav class="profile-segments" aria-label="我的音乐分区">
      <button
        v-for="item in sections"
        :key="item.key"
        type="button"
        :class="{ 'is-active': activeSection === item.key }"
        :aria-current="activeSection === item.key ? 'page' : undefined"
        @click="selectSection(item.key)"
      >
        <component :is="item.icon" :size="18" />
        {{ item.label }}
      </button>
    </nav>

    <section v-if="activeSection === 'playlists'" class="profile-section-panel">
      <div class="section-head">
        <div><p class="feature-label">创作空间</p><h2>创建的歌单</h2></div>
      </div>
      <form class="inline-form profile-create-form" @submit.prevent="createPlaylist">
        <label class="sr-only" for="new-playlist-title">新歌单名称</label>
        <input id="new-playlist-title" v-model.trim="playlistTitle" maxlength="100" placeholder="给新歌单起个名字" />
        <button type="submit" :disabled="!playlistTitle">创建歌单</button>
      </form>
      <div class="profile-server-list">
        <article v-for="playlist in playlistPage.items" :key="playlist.id">
          <RouterLink :to="`/playlists/${playlist.id}`">
            <span class="profile-item-cover">
              <img v-if="playlist.coverUrl" :src="resolveMediaUrl(playlist.coverUrl)" alt="" />
              <span v-else>♪</span>
            </span>
            <span><strong>{{ playlist.title }}</strong><small>{{ playlist.songCount }} 首 · {{ playlist.visibility === "PUBLIC" ? "公开" : "私有" }}</small></span>
          </RouterLink>
          <button type="button" class="danger-icon-action" :aria-label="`删除 ${playlist.title}`" @click="deletePlaylist(playlist)">
            <Trash2 :size="17" /><span>删除</span>
          </button>
        </article>
        <EmptyState v-if="!sectionLoading && !playlistPage.items.length">还没有歌单，先创建一个吧。</EmptyState>
      </div>
      <ProfilePagination :page="playlistPage.page" :size="playlistPage.size" :total="playlistPage.total" @change="loadPlaylists" />
    </section>

    <section v-else-if="activeSection === 'favorites'" class="profile-section-panel">
      <div class="section-head"><div><p class="feature-label">稍后再听</p><h2>收藏</h2></div></div>
      <div class="profile-server-list">
        <article v-for="favorite in favoritePage.items" :key="favorite.id">
          <RouterLink :to="favoritePath(favorite)">
            <span class="profile-item-cover">
              <img v-if="favoriteCover(favorite)" :src="resolveMediaUrl(favoriteCover(favorite))" alt="" />
              <span v-else>♥</span>
            </span>
            <span><strong>{{ favoriteTitle(favorite) }}</strong><small>{{ favoriteSubtitle(favorite) }}</small></span>
          </RouterLink>
          <button type="button" class="danger-icon-action" aria-label="取消收藏" @click="removeFavorite(favorite)">
            <HeartOff :size="17" /><span>取消</span>
          </button>
        </article>
        <EmptyState v-if="!sectionLoading && !favoritePage.items.length">收藏歌曲或公开歌单后会出现在这里。</EmptyState>
      </div>
      <ProfilePagination :page="favoritePage.page" :size="favoritePage.size" :total="favoritePage.total" @change="loadFavorites" />
    </section>

    <section v-else-if="activeSection === 'recent'" class="profile-section-panel">
      <div class="section-head">
        <div><p class="feature-label">听过的声音</p><h2>最近播放</h2></div>
        <button class="danger-icon-action" type="button" :disabled="!recentPage.total || clearingRecent" @click="clearRecentPlays">
          <Trash2 :size="17" /><span>{{ clearingRecent ? "清空中" : "清空" }}</span>
        </button>
      </div>
      <div class="profile-server-list">
        <article v-for="item in recentPage.items" :key="item.id">
          <RouterLink :to="`/songs/${item.song?.id || item.songId}`">
            <span class="profile-item-cover">
              <img v-if="item.song?.coverUrl" :src="resolveMediaUrl(item.song.coverUrl)" alt="" />
              <span v-else>▶</span>
            </span>
            <span><strong>{{ item.song?.title || `歌曲 #${item.songId}` }}</strong><small>{{ item.song?.artistName || "MeloSpace" }} · {{ formatDate(item.playedAt) }}</small></span>
          </RouterLink>
        </article>
        <EmptyState v-if="!sectionLoading && !recentPage.items.length">最近播放会按时间保存在这里。</EmptyState>
      </div>
      <ProfilePagination :page="recentPage.page" :size="recentPage.size" :total="recentPage.total" @change="loadRecent" />
    </section>

    <section v-else class="profile-section-panel profile-account-panel">
      <div class="section-head"><div><p class="feature-label">公开资料</p><h2>账户</h2></div></div>
      <form class="profile-editor-form" @submit.prevent="saveProfile">
        <label><span>昵称</span><input v-model.trim="profileNickname" maxlength="50" required /></label>
        <label class="profile-editor-bio"><span>简介 <small>{{ profileBio.length }}/500</small></span><textarea v-model="profileBio" maxlength="500" rows="4" /></label>
        <div class="profile-editor-actions">
          <input ref="avatarFileInput" class="sr-only" type="file" accept="image/jpeg,image/png,image/webp" @change="uploadAvatar" />
          <button class="secondary-action" type="button" :disabled="uploadingAvatar || savingProfile" @click="avatarFileInput?.click()">
            <Upload :size="17" />{{ uploadingAvatar ? "上传中…" : "更换头像" }}
          </button>
          <span class="profile-upload-status">{{ avatarUploadStatus }}</span>
          <button class="primary-action" type="submit" :disabled="savingProfile || uploadingAvatar || !profileNickname">{{ savingProfile ? "保存中…" : "保存资料" }}</button>
        </div>
      </form>
      <div class="profile-danger-zone">
        <div><strong>注销账号</strong><p>会永久删除歌单、收藏、评论和播放记录。</p></div>
        <button class="danger-action" type="button" @click="deleteMyAccount">注销账号</button>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref } from "vue";
import { Clock3, Heart, HeartOff, Library, Trash2, Upload, UserRound } from "lucide-vue-next";
import { useRouter } from "vue-router";
import { favoriteApi, playlistApi, uploadApi, userApi } from "@/api";
import type { FavoriteItem, PageResult, PlayHistoryItem, Playlist } from "@/api/types";
import EmptyState from "@/components/EmptyState.vue";
import { useAuthStore } from "@/stores/auth";
import { useUiStore } from "@/stores/ui";
import { resolveMediaUrl } from "@/utils/format";

type ProfileSection = "playlists" | "favorites" | "recent" | "account";
const PAGE_SIZE = 8;
const emptyPage = <T,>(): PageResult<T> => ({ items: [], page: 1, size: PAGE_SIZE, total: 0 });

const ProfilePagination = defineComponent({
  props: { page: { type: Number, required: true }, size: { type: Number, required: true }, total: { type: Number, required: true } },
  emits: ["change"],
  setup(props, { emit }) {
    return () => {
      const pages = Math.max(1, Math.ceil(props.total / props.size));
      if (pages <= 1) return null;
      return h("div", { class: "list-pagination" }, [
        h("button", { type: "button", disabled: props.page <= 1, onClick: () => emit("change", props.page - 1) }, "上一页"),
        h("span", `${props.page} / ${pages}`),
        h("button", { type: "button", disabled: props.page >= pages, onClick: () => emit("change", props.page + 1) }, "下一页")
      ]);
    };
  }
});

const ui = useUiStore();
const auth = useAuthStore();
const router = useRouter();
const activeSection = ref<ProfileSection>("playlists");
const sections = [
  { key: "playlists" as const, label: "创建的歌单", icon: Library },
  { key: "favorites" as const, label: "收藏", icon: Heart },
  { key: "recent" as const, label: "最近播放", icon: Clock3 },
  { key: "account" as const, label: "账户", icon: UserRound }
];
const playlistPage = ref<PageResult<Playlist>>(emptyPage());
const favoritePage = ref<PageResult<FavoriteItem>>(emptyPage());
const recentPage = ref<PageResult<PlayHistoryItem>>(emptyPage());
const sectionLoading = ref(false);
const playlistTitle = ref("");
const clearingRecent = ref(false);
const profileNickname = ref("");
const profileBio = ref("");
const profileAvatarUrl = ref("");
const avatarFileInput = ref<HTMLInputElement | null>(null);
const uploadingAvatar = ref(false);
const savingProfile = ref(false);
const avatarUploadStatus = ref("");
const profileInitial = computed(() => (profileNickname.value || auth.user?.username || "M").slice(0, 1).toUpperCase());

onMounted(async () => {
  syncProfile();
  await Promise.all([loadPlaylists(1), loadFavorites(1), loadRecent(1)]);
});

function syncProfile() {
  profileNickname.value = auth.user?.nickname || auth.user?.username || "";
  profileBio.value = auth.user?.bio || "";
  profileAvatarUrl.value = auth.user?.avatarUrl || "";
}

async function selectSection(section: ProfileSection) {
  activeSection.value = section;
  if (section === "playlists") await loadPlaylists(playlistPage.value.page);
  if (section === "favorites") await loadFavorites(favoritePage.value.page);
  if (section === "recent") await loadRecent(recentPage.value.page);
}

async function withLoading<T>(loader: () => Promise<T>) {
  sectionLoading.value = true;
  try { return await loader(); } finally { sectionLoading.value = false; }
}

async function loadPlaylists(page = 1) { playlistPage.value = await withLoading(() => userApi.playlists(page, PAGE_SIZE)); }
async function loadFavorites(page = 1) { favoritePage.value = await withLoading(() => userApi.favorites(page, PAGE_SIZE)); }
async function loadRecent(page = 1) { recentPage.value = await withLoading(() => userApi.recentPlays(page, PAGE_SIZE)); }

async function createPlaylist() {
  if (!playlistTitle.value) return;
  const detail = await playlistApi.create({ title: playlistTitle.value, visibility: "PUBLIC" });
  playlistTitle.value = "";
  ui.toast("歌单已创建");
  await router.push(`/playlists/${detail.id}`);
}

async function deletePlaylist(playlist: Playlist) {
  if (!window.confirm(`确定删除歌单「${playlist.title}」吗？该操作不可恢复。`)) return;
  await playlistApi.remove(playlist.id);
  ui.toast("歌单已删除");
  await loadPlaylists(playlistPage.value.page);
}

async function removeFavorite(favorite: FavoriteItem) {
  await favoriteApi.remove(favorite.targetType, favorite.targetId);
  ui.toast("已取消收藏");
  await loadFavorites(favoritePage.value.page);
}

function favoritePath(item: FavoriteItem) { return item.targetType === "PLAYLIST" ? `/playlists/${item.targetId}` : `/songs/${item.targetId}`; }
function favoriteTitle(item: FavoriteItem) { return item.targetType === "PLAYLIST" ? item.playlist?.title || `歌单 #${item.targetId}` : item.song?.title || `歌曲 #${item.targetId}`; }
function favoriteSubtitle(item: FavoriteItem) { return item.targetType === "PLAYLIST" ? `${item.playlist?.songCount || 0} 首歌曲` : item.song?.artistName || "歌曲"; }
function favoriteCover(item: FavoriteItem) { return item.targetType === "PLAYLIST" ? item.playlist?.coverUrl : item.song?.coverUrl; }
function formatDate(value: string) { return new Intl.DateTimeFormat("zh-CN", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }

async function clearRecentPlays() {
  if (!recentPage.value.total || clearingRecent.value || !window.confirm("确定清空最近播放列表吗？")) return;
  clearingRecent.value = true;
  try { await userApi.clearRecentPlays(); await loadRecent(1); ui.toast("最近播放已清空"); } finally { clearingRecent.value = false; }
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  uploadingAvatar.value = true;
  try {
    const upload = await uploadApi.image(file, "AVATAR");
    profileAvatarUrl.value = upload.url;
    avatarUploadStatus.value = "头像已上传，保存后生效";
  } finally { uploadingAvatar.value = false; }
}

async function saveProfile() {
  if (!profileNickname.value || savingProfile.value) return;
  savingProfile.value = true;
  try {
    await userApi.updateMe({ nickname: profileNickname.value, bio: profileBio.value || null, avatarUrl: profileAvatarUrl.value || null });
    await auth.refreshMe();
    syncProfile();
    avatarUploadStatus.value = "";
    ui.toast("个人资料已更新");
  } finally { savingProfile.value = false; }
}

async function deleteMyAccount() {
  if (!window.confirm("确定注销当前账号吗？账号的歌单、收藏、评论和播放记录会一起删除。")) return;
  await auth.deleteAccount();
  ui.toast("账号已注销");
  await router.push("/login");
}
</script>

<style scoped>
.profile-hub { display: grid; gap: 24px; min-width: 0; }
.profile-summary-card { grid-template-columns: minmax(0, 1fr) max-content; }
.profile-summary-stats { display: flex; gap: 10px; }
.profile-summary-stats button { display: grid; min-width: 100px; min-height: 68px; place-items: center; border: 1px solid rgba(49,79,68,.08); border-radius: 16px; padding: 10px; background: rgba(255,255,255,.78); box-shadow: inset 0 1px 0 rgba(255,255,255,.9); }
.profile-summary-stats strong { font-size: 22px; }
.profile-summary-stats span { color: var(--muted); font-size: 12px; }
.profile-segments { display: grid; gap: 8px; padding: 6px; border-radius: 18px; background: rgba(233,240,237,.76); grid-template-columns: repeat(4,minmax(0,1fr)); }
.profile-segments button { display: inline-flex; align-items: center; justify-content: center; min-height: 44px; gap: 7px; border-radius: 13px; color: var(--muted); font-weight: 680; }
.profile-segments button.is-active { border: 1px solid rgba(var(--brand-rgb),.1); background: #fff; color: var(--brand); box-shadow: 0 8px 22px rgba(42,76,64,.08); }
.profile-section-panel { min-width: 0; border: 1px solid rgba(49,79,68,.08); border-radius: 22px; padding: clamp(16px,3vw,28px); background: rgba(255,255,255,.76); box-shadow: var(--lg-inner-light), 0 18px 48px rgba(42,76,64,.065); }
.profile-create-form { max-width: 620px; margin-bottom: 18px; }
.profile-server-list { display: grid; gap: 8px; }
.profile-server-list article { display: grid; align-items: center; min-width: 0; gap: 8px; border-radius: 14px; padding: 6px 8px; grid-template-columns: minmax(0,1fr) max-content; }
.profile-server-list article:hover { background: rgba(239,246,243,.9); }
.profile-server-list article > a { display: grid; align-items: center; min-width: 0; min-height: 56px; gap: 12px; color: inherit; text-decoration: none; grid-template-columns: 48px minmax(0,1fr); }
.profile-server-list article > a > span:last-child { min-width: 0; }
.profile-server-list strong,.profile-server-list small { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.profile-server-list small { margin-top:4px; color:var(--muted); }
.profile-item-cover { display:grid; width:48px; height:48px; place-items:center; overflow:hidden; border-radius:10px; background:#edf3f0; color:var(--brand); }
.profile-item-cover img { width:100%; height:100%; object-fit:cover; }
.profile-server-list .danger-icon-action { min-width:44px; min-height:44px; }
.profile-account-panel .profile-editor-form { max-width: 760px; }
.profile-danger-zone { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-top:28px; border-top:1px solid var(--soft-line); padding-top:20px; }
.profile-danger-zone p { margin:4px 0 0; color:var(--muted); }
@media (max-width: 720px) {
  .profile-summary-card { grid-template-columns: 1fr; }
  .profile-summary-stats { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); }
  .profile-summary-stats button { min-width:0; }
  .profile-segments { overflow-x:auto; grid-template-columns:repeat(4,minmax(110px,1fr)); }
}
@media (max-width: 480px) {
  .profile-summary-stats { gap:6px; }
  .profile-summary-stats button { padding:7px 4px; }
  .profile-section-panel { padding:16px 12px; }
  .profile-danger-zone { align-items:flex-start; flex-direction:column; }
}
</style>
