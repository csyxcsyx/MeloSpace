<template>
  <template v-for="(segment, index) in segments" :key="index">
    <mark v-if="segment.matched" class="search-highlight">{{ segment.text }}</mark>
    <span v-else>{{ segment.text }}</span>
  </template>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { splitHighlight } from "@/utils/search";

const props = withDefaults(defineProps<{
  text?: string;
  keyword?: string;
}>(), {
  text: "",
  keyword: ""
});

const segments = computed(() => splitHighlight(props.text, props.keyword));
</script>
