<template>
  <aside class="sidebar">
    <RouterLink class="brand" to="/discover" aria-label="MeloSpace 首页">
      <img class="brand-icon" src="/melospace-icon.png" alt="" />
      <span>MeloSpace</span>
    </RouterLink>

    <form class="sidebar-search" @submit.prevent="submitSearch">
      <Search :size="18" />
      <input v-model.trim="keyword" placeholder="搜索" aria-label="搜索歌曲、歌手、专辑或歌单" />
    </form>

    <nav class="side-nav" aria-label="主导航">
      <RouterLink class="nav-link" to="/discover">
        <Grid2X2 :size="18" />
        新发现
      </RouterLink>
      <RouterLink class="nav-link" to="/songs">
        <ListMusic :size="18" />
        歌曲库
      </RouterLink>
      <RouterLink class="nav-link" to="/me">
        <Library :size="18" />
        我的音乐
      </RouterLink>
      <RouterLink v-if="auth.user?.role === 'ADMIN'" class="nav-link" to="/admin">
        <Settings :size="18" />
        后台管理
      </RouterLink>
    </nav>

    <div class="side-footer">
      <RouterLink v-if="!auth.isAuthenticated" class="login-button" to="/login">
        <UserRound :size="16" />
        登录
      </RouterLink>
      <template v-else>
        <div class="user-summary">
          <div class="avatar">{{ userInitial }}</div>
          <div>
            <strong>{{ auth.user?.nickname || auth.user?.username }}</strong>
            <span>{{ auth.user?.role === "ADMIN" ? "管理员" : "普通用户" }}</span>
          </div>
        </div>
        <button class="logout-button" type="button" @click="auth.logout()">退出登录</button>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { Grid2X2, Library, ListMusic, Search, Settings, UserRound } from "lucide-vue-next";
import { useAuthStore } from "@/stores/auth";

const router = useRouter();
const auth = useAuthStore();
const keyword = ref("");

const userInitial = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || "U";
  return name.slice(0, 1).toUpperCase();
});

function submitSearch() {
  router.push({ path: "/search", query: keyword.value ? { keyword: keyword.value } : undefined });
}
</script>
