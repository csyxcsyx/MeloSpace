<template>
  <div class="search-empty-state">
    <Search :size="25" aria-hidden="true" />
    <strong>没有找到匹配内容</strong>
    <p>试试缩短关键词、检查拼写，或搜索歌手和专辑名称。</p>
    <div class="search-empty-actions">
      <button v-for="starter in starters" :key="starter" type="button" @click="$emit('select', starter)">
        {{ starter }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Search } from "lucide-vue-next";

defineProps<{
  starters: string[];
}>();

defineEmits<{
  select: [keyword: string];
}>();
</script>
