<template>
  <section class="login-view">
    <div class="login-stack">
      <PageToolbar />
      <form class="login-card" @submit.prevent="submit">
        <h1>{{ mode === "login" ? "登录 MeloSpace" : "注册 MeloSpace" }}</h1>
        <p>使用后端初始化账号或新注册账号进入音乐网站。</p>
        <label>
          用户名
          <input
            v-model.trim="username"
            autocomplete="username"
            maxlength="50"
            minlength="3"
            :pattern="mode === 'register' ? '[A-Za-z]+' : undefined"
            required
            title="注册账号名只能包含英文字母"
          />
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
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import PageToolbar from "@/components/PageToolbar.vue";
import { useAuthStore } from "@/stores/auth";
import { useUiStore } from "@/stores/ui";

const auth = useAuthStore();
const ui = useUiStore();
const route = useRoute();
const router = useRouter();
const mode = ref<"login" | "register">("login");
const username = ref("");
const password = ref("");
const nickname = ref("");
const submitting = ref(false);
const USERNAME_PATTERN = /^[A-Za-z]+$/;

function toggleMode() {
  mode.value = mode.value === "login" ? "register" : "login";
}

async function submit() {
  if (mode.value === "register" && !USERNAME_PATTERN.test(username.value)) {
    ui.toast("账号名只能使用英文字母");
    return;
  }
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
