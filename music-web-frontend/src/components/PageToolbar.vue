<template>
  <nav class="page-toolbar" aria-label="页面导航">
    <button class="page-toolbar-action" type="button" @click="goBack">
      <ArrowLeft :size="17" />
      <span>返回</span>
    </button>
    <RouterLink class="page-toolbar-action" to="/discover">
      <Home :size="17" />
      <span>首页</span>
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { RouterLink, useRouter } from "vue-router";
import { ArrowLeft, Home } from "lucide-vue-next";

const router = useRouter();

function goBack() {
  if (window.history.state?.back) {
    router.back();
    return;
  }
  router.push("/discover");
}
</script>
