<template>
  <section class="login-view">
    <form class="login-card" @submit.prevent="submit">
      <h1>{{ mode === "login" ? "登录 Orange Music" : "注册 Orange Music" }}</h1>
      <p>使用后端初始化账号或新注册账号进入音乐网站。</p>
      <label>
        用户名
        <input v-model.trim="username" autocomplete="username" required />
      </label>
      <label>
        密码
        <input v-model="password" autocomplete="current-password" type="password" required minlength="8" />
      </label>
      <label v-if="mode === 'register'">
        昵称
        <input v-model.trim="nickname" autocomplete="nickname" />
      </label>
      <button class="primary-action" type="submit" :disabled="submitting">
        {{ submitting ? "提交中..." : mode === "login" ? "登录" : "注册" }}
      </button>
      <button class="link-button" type="button" @click="toggleMode">
        {{ mode === "login" ? "没有账号？去注册" : "已有账号？去登录" }}
      </button>
    </form>
  </section>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const mode = ref<"login" | "register">("login");
const username = ref("");
const password = ref("");
const nickname = ref("");
const submitting = ref(false);

function toggleMode() {
  mode.value = mode.value === "login" ? "register" : "login";
}

async function submit() {
  submitting.value = true;
  try {
    if (mode.value === "login") {
      await auth.login(username.value, password.value);
    } else {
      await auth.register(username.value, password.value, nickname.value);
    }
    router.push(String(route.query.redirect || "/discover"));
  } finally {
    submitting.value = false;
  }
}
</script>
