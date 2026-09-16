<template>
  <header class="page-header" :aria-labelledby="titleId">
    <div class="page-header-copy">
      <p v-if="eyebrow" class="feature-label page-header-eyebrow">{{ eyebrow }}</p>
      <h1 :id="titleId" class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-header-description">{{ description }}</p>
    </div>
    <div v-if="$slots.summary || $slots.actions" class="page-header-aside">
      <div v-if="$slots.summary" class="page-header-summary">
        <slot name="summary" />
      </div>
      <div v-if="$slots.actions" class="page-header-actions">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useId } from "vue";

defineProps<{
  eyebrow?: string;
  title: string;
  description?: string;
}>();

const titleId = `page-header-title-${useId().replace(/:/g, "")}`;
</script>
