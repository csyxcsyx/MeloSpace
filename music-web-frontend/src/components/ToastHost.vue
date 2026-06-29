<template>
  <div class="toast-host" aria-live="polite" aria-atomic="true">
    <button v-for="item in ui.toasts" :key="item.id" class="toast" type="button" @click="ui.dismiss(item.id)">
      {{ item.message }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { useUiStore } from "@/stores/ui";

const ui = useUiStore();
</script>
