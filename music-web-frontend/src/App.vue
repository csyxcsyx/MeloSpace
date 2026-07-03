<template>
  <div class="app-shell" :class="{ 'immersive-shell': isImmersive, 'discover-shell': isDiscover }">
    <AppSidebar v-if="!isImmersive" />
    <main class="main" :class="{ 'immersive-main': isImmersive, 'discover-main': isDiscover }">
      <RouterView v-slot="{ Component, route }">
        <KeepAlive v-if="route.meta.keepAlive">
          <component :is="Component" :key="route.fullPath" />
        </KeepAlive>
        <component :is="Component" v-else :key="route.fullPath" />
      </RouterView>
    </main>
    <GlobalPlayer :hidden="isImmersive" />
    <ToastHost />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { RouterView } from "vue-router";
import AppSidebar from "@/components/AppSidebar.vue";
import GlobalPlayer from "@/components/GlobalPlayer.vue";
import ToastHost from "@/components/ToastHost.vue";

const route = useRoute();
const isImmersive = computed(() => route.meta.immersive === true);
const isDiscover = computed(() => route.name === "discover" && !isImmersive.value);
</script>
