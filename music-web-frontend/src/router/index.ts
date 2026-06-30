import { createRouter, createWebHistory } from "vue-router";
import DiscoverView from "@/views/DiscoverView.vue";
import SearchView from "@/views/SearchView.vue";
import SongDetailView from "@/views/SongDetailView.vue";
import PlaylistDetailView from "@/views/PlaylistDetailView.vue";
import ProfileView from "@/views/ProfileView.vue";
import AdminView from "@/views/AdminView.vue";
import LoginView from "@/views/LoginView.vue";
import PlayerView from "@/views/PlayerView.vue";
import { useAuthStore } from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/discover" },
    { path: "/discover", name: "discover", component: DiscoverView },
    { path: "/search", name: "search", component: SearchView },
    { path: "/songs/:id", name: "song-detail", component: SongDetailView, props: true },
    { path: "/playlists/:id", name: "playlist-detail", component: PlaylistDetailView, props: true },
    { path: "/me", name: "profile", component: ProfileView, meta: { requiresAuth: true } },
    { path: "/admin", name: "admin", component: AdminView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: "/login", name: "login", component: LoginView },
    { path: "/player", name: "player", component: PlayerView, meta: { immersive: true } }
  ],
  scrollBehavior() {
    return { top: 0 };
  }
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (to.meta.requiresAdmin && auth.user?.role !== "ADMIN") {
    return { name: "discover" };
  }
  return true;
});

export default router;
