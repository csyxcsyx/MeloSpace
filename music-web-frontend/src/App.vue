<template>
  <div class="app-shell">
    <AppSidebar />
    <main class="main">
      <RouterView />
    </main>
    <GlobalPlayer />
    <ToastHost />
  </div>
</template>

<script setup lang="ts">
import { RouterView } from "vue-router";
import AppSidebar from "@/components/AppSidebar.vue";
import GlobalPlayer from "@/components/GlobalPlayer.vue";
import ToastHost from "@/components/ToastHost.vue";
</script>
