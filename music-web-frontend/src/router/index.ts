import { createRouter, createWebHistory } from "vue-router";
import DiscoverView from "@/views/DiscoverView.vue";
import SearchView from "@/views/SearchView.vue";
import AlbumDetailView from "@/views/AlbumDetailView.vue";
import ArtistDetailView from "@/views/ArtistDetailView.vue";
import SongDetailView from "@/views/SongDetailView.vue";
import PlaylistDetailView from "@/views/PlaylistDetailView.vue";
import ProfileView from "@/views/ProfileView.vue";
import AdminView from "@/views/AdminView.vue";
import LoginView from "@/views/LoginView.vue";
import PlayerView from "@/views/PlayerView.vue";
import { useAuthStore } from "@/stores/auth";

interface StoredScrollPosition {
  left: number;
  top: number;
}

const SCROLL_POSITIONS_KEY = "melospace-scroll-positions";
const scrollPositions: Record<string, StoredScrollPosition> = readScrollPositions();

function readScrollPositions() {
  if (typeof window === "undefined") return {};
  const raw = sessionStorage.getItem(SCROLL_POSITIONS_KEY);
  if (!raw) return {};
  try {
    return JSON.parse(raw) as Record<string, StoredScrollPosition>;
  } catch {
    sessionStorage.removeItem(SCROLL_POSITIONS_KEY);
    return {};
  }
}

function persistScrollPositions() {
  if (typeof window === "undefined") return;
  sessionStorage.setItem(SCROLL_POSITIONS_KEY, JSON.stringify(scrollPositions));
}

function saveScrollPosition(fullPath: string) {
  if (typeof window === "undefined" || !fullPath) return;
  scrollPositions[fullPath] = {
    left: window.scrollX,
    top: window.scrollY
  };
  persistScrollPositions();
}

function getScrollPosition(fullPath: string) {
  const position = scrollPositions[fullPath];
  if (!position) return null;
  return { left: position.left, top: position.top };
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/discover" },
    { path: "/discover", name: "discover", component: DiscoverView, meta: { keepAlive: true } },
    { path: "/search", name: "search", component: SearchView, meta: { keepAlive: true } },
    { path: "/albums/:id", name: "album-detail", component: AlbumDetailView, props: true, meta: { keepAlive: true } },
    { path: "/artists/:id", name: "artist-detail", component: ArtistDetailView, props: true, meta: { keepAlive: true } },
    { path: "/songs/:id", name: "song-detail", component: SongDetailView, props: true, meta: { keepAlive: true } },
    { path: "/playlists/:id", name: "playlist-detail", component: PlaylistDetailView, props: true, meta: { keepAlive: true } },
    { path: "/me", name: "profile", component: ProfileView, meta: { requiresAuth: true } },
    { path: "/admin", name: "admin", component: AdminView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: "/login", name: "login", component: LoginView },
    { path: "/player", name: "player", component: PlayerView, meta: { immersive: true } }
  ],
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition;
    if (to.meta.keepAlive) {
      return getScrollPosition(to.fullPath) ?? { top: 0 };
    }
    return { top: 0 };
  }
});

router.beforeEach((to, from) => {
  saveScrollPosition(from.fullPath);
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (to.meta.requiresAdmin && auth.user?.role !== "ADMIN") {
    return { name: "discover" };
  }
  return true;
});

if (typeof window !== "undefined") {
  window.addEventListener("pagehide", () => {
    saveScrollPosition(router.currentRoute.value.fullPath);
  });
}

export default router;
