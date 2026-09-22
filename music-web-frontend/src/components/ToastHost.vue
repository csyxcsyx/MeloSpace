<template>
  <TransitionGroup
    name="toast"
    tag="div"
    class="toast-host"
    role="status"
    aria-live="polite"
    aria-atomic="true"
  >
    <button
      v-for="item in ui.toasts"
      :key="item.id"
      class="toast"
      type="button"
      aria-label="关闭提示"
      @click="ui.dismiss(item.id)"
    >
      {{ item.message }}
    </button>
  </TransitionGroup>
</template>

<script setup lang="ts">
import { useUiStore } from "@/stores/ui";

const ui = useUiStore();
</script>
