<template>
  <aside class="sidebar">
    <div class="sidebar-top-row">
      <RouterLink class="brand" to="/discover" aria-label="MeloSpace 首页">
        <img class="brand-icon" src="/melospace-icon.png" alt="" />
        <span>MeloSpace</span>
      </RouterLink>
      <button
        ref="mobileAccountTriggerRef"
        class="mobile-account-trigger"
        type="button"
        aria-haspopup="menu"
        :aria-expanded="accountMenuOpen"
        :aria-controls="accountMenuId"
        :aria-label="auth.isAuthenticated ? '打开账户菜单' : '打开登录菜单'"
        @click="toggleAccountMenu"
      >
        <img
          v-if="auth.user?.avatarUrl"
          class="account-avatar-image"
          :src="resolveMediaUrl(auth.user.avatarUrl)"
          alt=""
        />
        <span v-else-if="auth.isAuthenticated" class="avatar account-avatar">{{ userInitial }}</span>
        <UserRound v-else :size="21" aria-hidden="true" />
      </button>
    </div>

    <form class="sidebar-search" @submit.prevent="submitSearch">
      <Search :size="18" aria-hidden="true" />
      <label class="sr-only" for="sidebar-search-input">搜索歌曲、歌手、专辑或歌单</label>
      <input
        id="sidebar-search-input"
        v-model.trim="keyword"
        name="keyword"
        type="search"
        maxlength="50"
        placeholder="搜索"
        autocomplete="off"
      />
    </form>

    <nav class="side-nav" aria-label="主导航">
      <RouterLink class="nav-link" to="/discover">
        <Grid2X2 :size="18" aria-hidden="true" />
        <span class="desktop-nav-label">新发现</span>
        <span class="mobile-nav-label">发现</span>
      </RouterLink>
      <RouterLink class="nav-link mobile-nav-only" to="/search">
        <Search :size="18" aria-hidden="true" />
        <span>搜索</span>
      </RouterLink>
      <RouterLink class="nav-link" to="/songs">
        <ListMusic :size="18" aria-hidden="true" />
        <span>歌曲库</span>
      </RouterLink>
      <RouterLink class="nav-link" to="/me">
        <Library :size="18" aria-hidden="true" />
        <span class="desktop-nav-label">我的音乐</span>
        <span class="mobile-nav-label">我的</span>
      </RouterLink>
    </nav>

    <div class="side-footer">
      <button
        ref="desktopAccountTriggerRef"
        class="desktop-account-trigger"
        type="button"
        aria-haspopup="menu"
        :aria-expanded="accountMenuOpen"
        :aria-controls="accountMenuId"
        @click="toggleAccountMenu"
      >
        <template v-if="auth.isAuthenticated">
          <img
            v-if="auth.user?.avatarUrl"
            class="account-avatar-image"
            :src="resolveMediaUrl(auth.user.avatarUrl)"
            alt=""
          />
          <span v-else class="avatar">{{ userInitial }}</span>
          <span class="user-summary-copy">
            <strong>{{ auth.user?.nickname || auth.user?.username }}</strong>
            <span>{{ auth.user?.role === "ADMIN" ? "管理员" : "普通用户" }}</span>
          </span>
          <ChevronUp :size="17" aria-hidden="true" />
        </template>
        <template v-else>
          <UserRound :size="18" aria-hidden="true" />
          <span>登录或注册</span>
          <ChevronUp :size="17" aria-hidden="true" />
        </template>
      </button>
    </div>

    <div
      v-if="accountMenuOpen"
      :id="accountMenuId"
      ref="accountMenuRef"
      class="account-menu"
      role="menu"
      aria-label="账户菜单"
      @keydown="handleMenuKeydown"
    >
      <header v-if="auth.isAuthenticated" class="account-menu-user">
        <strong>{{ auth.user?.nickname || auth.user?.username }}</strong>
        <span>{{ auth.user?.role === "ADMIN" ? "管理员账户" : "MeloSpace 用户" }}</span>
      </header>
      <RouterLink
        v-if="!auth.isAuthenticated"
        :to="loginTarget"
        role="menuitem"
        @click="closeAccountMenu"
      >
        <LogIn :size="18" aria-hidden="true" />
        登录或注册
      </RouterLink>
      <template v-else>
        <RouterLink to="/me" role="menuitem" @click="closeAccountMenu">
          <UserRound :size="18" aria-hidden="true" />
          我的音乐
        </RouterLink>
        <RouterLink
          v-if="auth.user?.role === 'ADMIN'"
          to="/admin"
          role="menuitem"
          @click="closeAccountMenu"
        >
          <Settings :size="18" aria-hidden="true" />
          后台管理
        </RouterLink>
        <button class="account-menu-logout" type="button" role="menuitem" @click="logout">
          <LogOut :size="18" aria-hidden="true" />
          退出登录
        </button>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ChevronUp,
  Grid2X2,
  Library,
  ListMusic,
  LogIn,
  LogOut,
  Search,
  Settings,
  UserRound
} from "lucide-vue-next";
import { useAuthStore } from "@/stores/auth";
import { resolveMediaUrl } from "@/utils/format";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const keyword = ref("");
const accountMenuOpen = ref(false);
const accountMenuRef = ref<HTMLElement | null>(null);
const mobileAccountTriggerRef = ref<HTMLButtonElement | null>(null);
const desktopAccountTriggerRef = ref<HTMLButtonElement | null>(null);
const activeAccountTrigger = ref<HTMLButtonElement | null>(null);
const accountMenuId = "melospace-account-menu";

const userInitial = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || "U";
  return name.slice(0, 1).toUpperCase();
});

const loginTarget = computed(() => {
  if (route.name === "login") return { name: "login" };
  return { name: "login", query: { redirect: route.fullPath } };
});

function submitSearch() {
  router.push({ path: "/search", query: keyword.value ? { keyword: keyword.value } : undefined });
}

async function toggleAccountMenu(event: MouseEvent) {
  activeAccountTrigger.value = event.currentTarget as HTMLButtonElement;
  accountMenuOpen.value = !accountMenuOpen.value;
  if (!accountMenuOpen.value) return;
  await nextTick();
  firstMenuItem()?.focus();
}

function closeAccountMenu({ restoreFocus = false } = {}) {
  if (!accountMenuOpen.value) return;
  accountMenuOpen.value = false;
  if (restoreFocus) {
    nextTick(() => activeAccountTrigger.value?.focus());
  }
}

function menuItems() {
  return Array.from(
    accountMenuRef.value?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []
  );
}

function firstMenuItem() {
  return menuItems()[0];
}

function handleMenuKeydown(event: KeyboardEvent) {
  const items = menuItems();
  if (!items.length) return;
  const currentIndex = items.indexOf(document.activeElement as HTMLElement);
  let nextIndex: number | null = null;

  if (event.key === "ArrowDown") {
    nextIndex = currentIndex < items.length - 1 ? currentIndex + 1 : 0;
  } else if (event.key === "ArrowUp") {
    nextIndex = currentIndex > 0 ? currentIndex - 1 : items.length - 1;
  } else if (event.key === "Home") {
    nextIndex = 0;
  } else if (event.key === "End") {
    nextIndex = items.length - 1;
  } else if (event.key === "Escape") {
    event.preventDefault();
    closeAccountMenu({ restoreFocus: true });
    return;
  }

  if (nextIndex !== null) {
    event.preventDefault();
    items[nextIndex]?.focus();
  }
}

function handlePointerDown(event: PointerEvent) {
  if (!accountMenuOpen.value) return;
  const target = event.target as Node;
  const triggerContainsTarget =
    mobileAccountTriggerRef.value?.contains(target) ||
    desktopAccountTriggerRef.value?.contains(target);
  if (triggerContainsTarget || accountMenuRef.value?.contains(target)) return;
  closeAccountMenu();
}

async function logout() {
  try {
    await auth.logout();
  } finally {
    closeAccountMenu();
    if (route.meta.requiresAuth || route.meta.requiresAdmin) {
      await router.push("/discover");
    }
  }
}

watch(
  () => route.fullPath,
  () => closeAccountMenu()
);

onMounted(() => {
  document.addEventListener("pointerdown", handlePointerDown);
});

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", handlePointerDown);
});
</script>
