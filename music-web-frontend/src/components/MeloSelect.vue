<template>
  <div ref="rootRef" class="melo-select-field" :class="{ 'is-disabled': disabled }">
    <span v-if="showLabel" :id="labelId" class="melo-select-label">{{ label }}</span>
    <button
      :id="id"
      ref="triggerRef"
      class="melo-select-trigger"
      type="button"
      role="combobox"
      aria-haspopup="listbox"
      :aria-label="showLabel ? undefined : label"
      :aria-labelledby="showLabel ? labelId : undefined"
      :aria-controls="listboxId"
      :aria-expanded="open"
      :aria-activedescendant="open && activeIndex >= 0 ? optionId(activeIndex) : undefined"
      :aria-required="required || undefined"
      :disabled="disabled"
      @click="toggle"
      @keydown="handleKeydown"
    >
      <span class="melo-select-value">{{ selectedOption?.label ?? placeholder }}</span>
      <ChevronDown class="melo-select-chevron" :class="{ open }" :size="16" aria-hidden="true" />
    </button>

    <Teleport to="body">
      <Transition name="melo-select-pop">
        <div
          v-if="open"
          :id="listboxId"
          ref="listboxRef"
          class="melo-select-menu"
          :style="menuStyle"
          role="listbox"
          :aria-labelledby="showLabel ? labelId : undefined"
          :aria-label="showLabel ? undefined : label"
          @pointerdown.stop
        >
          <button
            v-for="(option, index) in options"
            :id="optionId(index)"
            :key="`${String(option.value)}-${index}`"
            class="melo-select-option"
            :class="{ 'is-active': index === activeIndex, 'is-selected': isSelected(option) }"
            type="button"
            role="option"
            tabindex="-1"
            :aria-selected="isSelected(option)"
            :aria-disabled="option.disabled || undefined"
            :disabled="option.disabled"
            @pointerenter="setActive(index)"
            @click="selectOption(option)"
          >
            <span>{{ option.label }}</span>
            <Check v-if="isSelected(option)" :size="16" aria-hidden="true" />
          </button>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from "vue";
import { Check, ChevronDown } from "lucide-vue-next";

export type MeloSelectValue = string | number;
export interface MeloSelectOption {
  value: MeloSelectValue;
  label: string;
  disabled?: boolean;
}

const props = withDefaults(defineProps<{
  modelValue: MeloSelectValue;
  options: MeloSelectOption[];
  label: string;
  id?: string;
  disabled?: boolean;
  required?: boolean;
  showLabel?: boolean;
  placeholder?: string;
}>(), {
  id: undefined,
  disabled: false,
  required: false,
  showLabel: true,
  placeholder: "请选择"
});

const emit = defineEmits<{
  "update:modelValue": [value: MeloSelectValue];
  change: [value: MeloSelectValue];
}>();

const instanceId = useId().replace(/:/g, "");
const labelId = `melo-select-label-${instanceId}`;
const listboxId = `melo-select-listbox-${instanceId}`;
const rootRef = ref<HTMLElement | null>(null);
const triggerRef = ref<HTMLButtonElement | null>(null);
const listboxRef = ref<HTMLElement | null>(null);
const open = ref(false);
const activeIndex = ref(-1);
const menuStyle = ref<Record<string, string>>({});
let typeahead = "";
let typeaheadTimer: number | undefined;

const selectedOption = computed(() => props.options.find((option) => option.value === props.modelValue));

watch(() => props.disabled, (disabled) => {
  if (disabled) close();
});

onMounted(() => {
  document.addEventListener("pointerdown", handleOutsidePointer, true);
  window.addEventListener("resize", handleViewportChange);
  window.addEventListener("scroll", handleViewportChange, true);
});

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", handleOutsidePointer, true);
  window.removeEventListener("resize", handleViewportChange);
  window.removeEventListener("scroll", handleViewportChange, true);
  if (typeaheadTimer) window.clearTimeout(typeaheadTimer);
});

function optionId(index: number) {
  return `melo-select-option-${instanceId}-${index}`;
}

function isSelected(option: MeloSelectOption) {
  return option.value === props.modelValue;
}

function firstEnabledIndex() {
  return props.options.findIndex((option) => !option.disabled);
}

function lastEnabledIndex() {
  for (let index = props.options.length - 1; index >= 0; index -= 1) {
    if (!props.options[index].disabled) return index;
  }
  return -1;
}

function selectedIndex() {
  const index = props.options.findIndex((option) => isSelected(option) && !option.disabled);
  return index >= 0 ? index : firstEnabledIndex();
}

async function toggle() {
  if (open.value) {
    close();
    return;
  }
  await openMenu();
}

async function openMenu(preferredIndex = selectedIndex()) {
  if (props.disabled || !props.options.length) return;
  open.value = true;
  activeIndex.value = preferredIndex;
  await nextTick();
  positionMenu();
  scrollActiveIntoView();
}

function close(restoreFocus = false) {
  open.value = false;
  menuStyle.value = {};
  clearTypeahead();
  if (restoreFocus) nextTick(() => triggerRef.value?.focus());
}

function selectOption(option: MeloSelectOption) {
  if (option.disabled) return;
  emit("update:modelValue", option.value);
  emit("change", option.value);
  close(true);
}

function setActive(index: number) {
  if (!props.options[index]?.disabled) activeIndex.value = index;
}

function moveActive(direction: 1 | -1) {
  if (!props.options.length) return;
  let index = activeIndex.value;
  for (let step = 0; step < props.options.length; step += 1) {
    index = (index + direction + props.options.length) % props.options.length;
    if (!props.options[index]?.disabled) {
      activeIndex.value = index;
      nextTick(scrollActiveIntoView);
      return;
    }
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    if (!open.value) void openMenu();
    else moveActive(event.key === "ArrowDown" ? 1 : -1);
    return;
  }
  if (event.key === "Home" || event.key === "End") {
    event.preventDefault();
    if (!open.value) void openMenu(event.key === "Home" ? firstEnabledIndex() : lastEnabledIndex());
    else activeIndex.value = event.key === "Home" ? firstEnabledIndex() : lastEnabledIndex();
    nextTick(scrollActiveIntoView);
    return;
  }
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    if (!open.value) void openMenu();
    else if (activeIndex.value >= 0) selectOption(props.options[activeIndex.value]);
    return;
  }
  if (event.key === "Escape" && open.value) {
    event.preventDefault();
    close(true);
    return;
  }
  if (event.key === "Tab") {
    close();
    return;
  }
  if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
    searchByPrefix(event.key);
  }
}

function searchByPrefix(character: string) {
  typeahead += character.toLocaleLowerCase();
  if (typeaheadTimer) window.clearTimeout(typeaheadTimer);
  typeaheadTimer = window.setTimeout(clearTypeahead, 650);
  const start = Math.max(activeIndex.value, -1);
  const ordered = [...props.options.slice(start + 1), ...props.options.slice(0, start + 1)];
  const match = ordered.find((option) => !option.disabled && option.label.toLocaleLowerCase().startsWith(typeahead));
  if (!match) return;
  const index = props.options.indexOf(match);
  if (!open.value) void openMenu(index);
  else {
    activeIndex.value = index;
    nextTick(scrollActiveIntoView);
  }
}

function clearTypeahead() {
  typeahead = "";
  typeaheadTimer = undefined;
}

function handleOutsidePointer(event: PointerEvent) {
  const target = event.target as Node;
  if (rootRef.value?.contains(target) || listboxRef.value?.contains(target)) return;
  close();
}

function handleViewportChange() {
  if (open.value) positionMenu();
}

function positionMenu() {
  const trigger = triggerRef.value;
  const menu = listboxRef.value;
  if (!trigger || !menu) return;
  const triggerRect = trigger.getBoundingClientRect();
  const menuRect = menu.getBoundingClientRect();
  const edge = 10;
  const width = Math.min(Math.max(triggerRect.width, 180), window.innerWidth - edge * 2);
  const left = Math.min(window.innerWidth - width - edge, Math.max(edge, triggerRect.left));
  const spaceBelow = window.innerHeight - triggerRect.bottom - edge;
  const top = spaceBelow >= Math.min(menuRect.height, 320)
    ? triggerRect.bottom + 7
    : Math.max(edge, triggerRect.top - menuRect.height - 7);
  menuStyle.value = {
    left: `${Math.round(left)}px`,
    top: `${Math.round(top)}px`,
    width: `${Math.round(width)}px`
  };
}

function scrollActiveIntoView() {
  const option = listboxRef.value?.querySelector<HTMLElement>(`#${optionId(activeIndex.value)}`);
  if (typeof option?.scrollIntoView === "function") option.scrollIntoView({ block: "nearest" });
}
</script>

<style scoped>
.melo-select-field {
  display: grid;
  min-width: 0;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 720;
}

.melo-select-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.melo-select-trigger {
  display: grid;
  width: 100%;
  min-width: 0;
  min-height: 44px;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 10px 8px 12px;
  background: rgba(255, 255, 255, 0.86);
  color: var(--text);
  grid-template-columns: minmax(0, 1fr) max-content;
  text-align: left;
  transition: border-color 160ms ease, background 160ms ease, box-shadow 160ms ease;
}

:global(.list-controls) .melo-select-trigger {
  min-height: 36px;
  border-radius: 8px;
  padding: 7px 10px;
}

:global(.admin-form) .melo-select-field {
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
}

:global(.admin-form) .melo-select-trigger {
  min-height: 40px;
  border-radius: 8px;
}

.melo-select-trigger:hover,
.melo-select-trigger[aria-expanded="true"] {
  border-color: rgba(var(--brand-rgb), 0.32);
  background: #fff;
}

.melo-select-trigger:focus-visible {
  border-color: rgba(var(--brand-rgb), 0.46);
  outline: 2px solid rgba(var(--brand-rgb), 0.25);
  outline-offset: 2px;
}

.melo-select-field.is-disabled {
  opacity: 0.52;
}

.melo-select-trigger:disabled {
  cursor: default;
}

.melo-select-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.melo-select-chevron {
  color: var(--muted);
  transition: transform 160ms ease;
}

.melo-select-chevron.open {
  transform: rotate(180deg);
}

.melo-select-menu {
  position: fixed;
  z-index: 2600;
  display: grid;
  max-height: min(320px, calc(100dvh - 20px));
  gap: 3px;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 7px;
  border: 1px solid rgba(199, 215, 208, 0.88);
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 22px 58px rgba(36, 65, 55, 0.18), inset 0 1px 0 #fff;
  backdrop-filter: blur(22px) saturate(150%);
  -webkit-backdrop-filter: blur(22px) saturate(150%);
}

.melo-select-option {
  display: grid;
  width: 100%;
  min-height: 40px;
  align-items: center;
  gap: 8px;
  border-radius: 10px;
  padding: 8px 10px;
  color: #35413c;
  grid-template-columns: minmax(0, 1fr) 18px;
  text-align: left;
}

.melo-select-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.melo-select-option:hover,
.melo-select-option.is-active {
  background: var(--brand-soft);
  color: var(--brand-strong);
}

.melo-select-option.is-selected {
  color: var(--brand);
  font-weight: 780;
}

.melo-select-option:disabled {
  opacity: 0.42;
  cursor: default;
}

.melo-select-pop-enter-active,
.melo-select-pop-leave-active {
  transition: opacity 130ms ease, transform 130ms ease;
}

.melo-select-pop-enter-from,
.melo-select-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.985);
}

@media (prefers-reduced-motion: reduce) {
  .melo-select-pop-enter-active,
  .melo-select-pop-leave-active,
  .melo-select-chevron {
    transition: none;
  }
}
</style>
