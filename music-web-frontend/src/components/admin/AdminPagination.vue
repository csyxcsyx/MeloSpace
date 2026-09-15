<template>
  <div v-if="pageCount > 1" class="list-pagination">
    <button type="button" :disabled="page <= 1" @click="$emit('change', page - 1)">上一页</button>
    <span>{{ page }} / {{ pageCount }}<template v-if="summary"> · {{ summary }}</template></span>
    <button type="button" :disabled="page >= pageCount" @click="$emit('change', page + 1)">下一页</button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  page: number;
  pageCount: number;
  summary?: string;
}>();

defineEmits<{
  change: [page: number];
}>();
</script>
