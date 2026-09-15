<template>
  <form class="playlist-editor glass-panel" @submit.prevent="$emit('save')">
    <div class="section-head playlist-panel-head">
      <div>
        <p class="feature-label">管理歌单</p>
        <h2>编辑资料</h2>
      </div>
      <button type="button" class="playlist-icon-button" aria-label="关闭编辑" @click="$emit('close')">
        <X :size="19" />
      </button>
    </div>
    <div class="playlist-editor-grid">
      <label>
        <span>歌单名称</span>
        <input v-model.trim="title" maxlength="100" required />
      </label>
      <MeloSelect v-model="visibility" label="公开状态" :options="visibilityOptions" />
      <label class="playlist-editor-wide">
        <span>描述</span>
        <textarea v-model.trim="description" maxlength="500" rows="4" />
        <small>{{ description.length }}/500</small>
      </label>
      <label class="playlist-editor-wide">
        <span>标签（使用逗号分隔，最多 5 个，每个最多 12 字）</span>
        <input v-model="tagsText" maxlength="69" placeholder="例如：华语流行, 通勤, 治愈" />
      </label>
      <div class="playlist-editor-wide playlist-cover-field">
        <span>歌单封面</span>
        <div class="playlist-cover-controls">
          <label class="secondary-action playlist-file-button">
            <ImagePlus :size="18" />
            {{ coverUploading ? "正在上传..." : "上传图片" }}
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              :disabled="coverUploading"
              @change="$emit('upload-cover', $event)"
            />
          </label>
          <input v-model.trim="coverUrl" aria-label="歌单封面地址" placeholder="或填写图片地址" />
        </div>
      </div>
    </div>
    <div class="playlist-editor-actions">
      <button type="button" class="secondary-action" @click="$emit('reset')">恢复</button>
      <button type="submit" class="primary-action" :disabled="saving || coverUploading">
        {{ saving ? "正在保存..." : "保存修改" }}
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ImagePlus, X } from "lucide-vue-next";
import MeloSelect, { type MeloSelectOption } from "@/components/MeloSelect.vue";

const visibilityOptions: MeloSelectOption[] = [
  { value: "PUBLIC", label: "公开，所有人可发现与评论" },
  { value: "PRIVATE", label: "私有，仅自己可见" }
];

defineProps<{
  saving: boolean;
  coverUploading: boolean;
}>();

const title = defineModel<string>("title", { required: true });
const description = defineModel<string>("description", { required: true });
const coverUrl = defineModel<string>("coverUrl", { required: true });
const visibility = defineModel<"PUBLIC" | "PRIVATE">("visibility", { required: true });
const tagsText = defineModel<string>("tagsText", { required: true });

defineEmits<{
  close: [];
  reset: [];
  save: [];
  "upload-cover": [event: Event];
}>();
</script>

<style scoped>
.playlist-editor { padding: clamp(18px, 3vw, 28px); }
.playlist-panel-head {
  align-items: center;
  margin-bottom: 18px;
}
.playlist-panel-head h2,
.playlist-panel-head p { margin: 0; }
.playlist-icon-button {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 50%;
  background: rgba(242, 242, 246, 0.9);
  color: #4f4f56;
}
.playlist-editor-grid {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.playlist-editor-grid label,
.playlist-editor-grid :deep(.melo-select-field),
.playlist-cover-field {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
}
.playlist-editor-grid label > span,
.playlist-cover-field > span {
  color: #52525a;
  font-size: 13px;
  font-weight: 680;
}
.playlist-editor-grid input,
.playlist-editor-grid select,
.playlist-editor-grid textarea,
.playlist-cover-controls > input {
  width: 100%;
  min-height: 44px;
  border: 1px solid rgba(209, 209, 216, 0.9);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.86);
  color: inherit;
}
.playlist-editor-grid textarea { resize: vertical; }
.playlist-editor-grid small { justify-self: end; color: var(--muted); }
.playlist-editor-wide { grid-column: 1 / -1; }
.playlist-cover-controls {
  display: grid;
  gap: 10px;
  grid-template-columns: max-content minmax(0, 1fr);
}
.playlist-file-button {
  display: inline-flex !important;
  align-items: center;
  min-height: 44px;
  gap: 7px !important;
  cursor: pointer;
}
.playlist-file-button input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}
.playlist-editor-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
.playlist-editor-actions button { min-height: 44px; }

@media (max-width: 620px) {
  .playlist-editor-grid,
  .playlist-cover-controls { grid-template-columns: minmax(0, 1fr); }
  .playlist-editor-wide { grid-column: auto; }
  .playlist-editor-actions { flex-wrap: wrap; }
}

@media (max-width: 390px) {
  .playlist-editor { padding: 16px; }
}
</style>
