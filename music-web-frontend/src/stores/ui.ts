import { defineStore } from "pinia";
import { ref } from "vue";

export interface ToastItem {
  id: number;
  message: string;
}

export const useUiStore = defineStore("ui", () => {
  const toasts = ref<ToastItem[]>([]);
  let nextId = 1;

  function toast(message: string) {
    const id = nextId++;
    toasts.value.push({ id, message });
    window.setTimeout(() => dismiss(id), 3200);
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((item) => item.id !== id);
  }

  return { toasts, toast, dismiss };
});
