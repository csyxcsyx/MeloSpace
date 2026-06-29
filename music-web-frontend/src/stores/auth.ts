import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { authApi, userApi } from "@/api";
import type { UserSummary } from "@/api/types";

const TOKEN_KEY = "music-web-token";
const USER_KEY = "music-web-user";

function readUser(): UserSummary | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserSummary;
  } catch {
    localStorage.removeItem(USER_KEY);
    return null;
  }
}

export const useAuthStore = defineStore("auth", () => {
  const token = ref(localStorage.getItem(TOKEN_KEY));
  const user = ref<UserSummary | null>(readUser());
  const isAuthenticated = computed(() => Boolean(token.value && user.value));

  function persist(nextToken: string, nextUser: UserSummary) {
    token.value = nextToken;
    user.value = nextUser;
    localStorage.setItem(TOKEN_KEY, nextToken);
    localStorage.setItem(USER_KEY, JSON.stringify(nextUser));
  }

  function clearSession() {
    token.value = null;
    user.value = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  async function login(username: string, password: string) {
    const response = await authApi.login(username, password);
    persist(response.token, response.user);
  }

  async function register(username: string, password: string, nickname?: string) {
    const response = await authApi.register(username, password, nickname);
    persist(response.token, response.user);
  }

  async function refreshMe() {
    if (!token.value) return;
    user.value = await userApi.me();
    localStorage.setItem(USER_KEY, JSON.stringify(user.value));
  }

  async function logout() {
    try {
      await authApi.logout();
    } finally {
      clearSession();
    }
  }

  return { token, user, isAuthenticated, login, register, refreshMe, logout, clearSession };
});
