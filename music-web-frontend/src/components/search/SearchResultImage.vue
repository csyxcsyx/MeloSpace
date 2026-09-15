<template>
  <span
    class="search-result-image"
    :class="{ 'is-circle': shape === 'circle', 'is-large': size === 'large' }"
  >
    <img v-if="url" :src="resolveMediaUrl(url)" alt="" />
    <component v-else :is="fallback" :size="size === 'large' ? 25 : 18" aria-hidden="true" />
  </span>
</template>

<script setup lang="ts">
import type { Component } from "vue";
import { resolveMediaUrl } from "@/utils/format";

withDefaults(defineProps<{
  url?: string | null;
  shape?: "square" | "circle";
  size?: "default" | "large";
  fallback: Component;
}>(), {
  url: null,
  shape: "square",
  size: "default"
});
</script>
