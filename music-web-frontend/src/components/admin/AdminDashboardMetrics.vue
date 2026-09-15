<template>
  <section class="metrics">
    <div v-for="metric in metrics" :key="metric.key" class="metric">
      <strong>{{ dashboard[metric.key] ?? 0 }}</strong>
      <span>{{ metric.label }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  dashboard: Record<string, number>;
}>();

const metrics = [
  { key: "users", label: "用户" },
  { key: "songs", label: "歌曲" },
  { key: "playlists", label: "歌单" },
  { key: "comments", label: "评论" }
] as const;
</script>
