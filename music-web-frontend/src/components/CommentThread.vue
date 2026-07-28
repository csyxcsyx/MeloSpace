<template>
  <section class="comment-thread" aria-labelledby="comment-thread-title">
    <div class="comment-thread-heading">
      <div>
        <p class="comment-eyebrow">听友讨论</p>
        <h2 id="comment-thread-title">评论 <span>{{ total }}</span></h2>
      </div>
      <div class="comment-sort" role="group" aria-label="评论排序">
        <button type="button" :class="{ active: sort === 'HOT' }" @click="setSort('HOT')">最热</button>
        <button type="button" :class="{ active: sort === 'LATEST' }" @click="setSort('LATEST')">最新</button>
      </div>
    </div>

    <form v-if="auth.isAuthenticated" class="comment-composer" @submit.prevent="submitRootComment">
      <span class="comment-avatar comment-avatar-self">
        <img v-if="auth.user?.avatarUrl" :src="resolveMediaUrl(auth.user.avatarUrl)" alt="" />
        <UserRound v-else :size="20" aria-hidden="true" />
      </span>
      <label>
        <span class="sr-only">写评论</span>
        <textarea
          v-model="rootDraft"
          maxlength="500"
          rows="3"
          placeholder="分享你此刻的感受…"
          :disabled="postingRoot"
        />
        <small>{{ rootDraft.length }}/500</small>
      </label>
      <button class="primary-action" type="submit" :disabled="postingRoot || !rootDraft.trim()">
        {{ postingRoot ? "发布中…" : "发布" }}
      </button>
    </form>
    <button v-else class="comment-login-prompt" type="button" @click="goToLogin">
      登录后参与评论、回复和点赞
      <ArrowRight :size="17" aria-hidden="true" />
    </button>

    <div v-if="errorMessage" class="comment-feedback" role="alert">
      <span>{{ errorMessage }}</span>
      <button type="button" @click="loadComments">重试</button>
    </div>
    <div v-else-if="loading" class="comment-loading" aria-live="polite">正在加载评论…</div>

    <div v-else-if="comments.length" class="comment-thread-list">
      <article
        v-for="comment in comments"
        :key="comment.id"
        class="comment-card"
        :class="{ 'is-pinned': comment.pinned, 'is-deleted': comment.deleted }"
      >
        <RouterLink class="comment-avatar" :to="`/users/${comment.userId}`" :aria-label="`查看 ${comment.userNickname} 的主页`">
          <img v-if="comment.userAvatarUrl" :src="resolveMediaUrl(comment.userAvatarUrl)" alt="" />
          <UserRound v-else :size="20" aria-hidden="true" />
        </RouterLink>
        <div class="comment-body">
          <header>
            <RouterLink :to="`/users/${comment.userId}`">{{ comment.userNickname }}</RouterLink>
            <span v-if="comment.pinned" class="comment-pinned"><Pin :size="13" aria-hidden="true" />置顶</span>
            <time :datetime="comment.createdAt">{{ relativeTime(comment.createdAt) }}</time>
          </header>
          <p class="comment-content">{{ comment.content }}</p>
          <div v-if="!comment.deleted" class="comment-actions">
            <button
              type="button"
              :class="{ active: comment.liked }"
              :disabled="pendingLikes.has(comment.id)"
              :aria-label="comment.liked ? '取消点赞' : '点赞'"
              @click="toggleLike(comment)"
            >
              <Heart :size="16" :fill="comment.liked ? 'currentColor' : 'none'" aria-hidden="true" />
              <span>{{ comment.likeCount || "点赞" }}</span>
            </button>
            <button type="button" @click="openReply(comment, comment)">
              <MessageCircle :size="16" aria-hidden="true" />
              回复
            </button>
            <button type="button" @click="toggleReplies(comment)">
              <MessagesSquare :size="16" aria-hidden="true" />
              {{ expandedReplies.has(comment.id) ? "收起回复" : `${comment.replyCount} 条回复` }}
            </button>
            <button v-if="comment.mine" type="button" class="danger" @click="removeComment(comment)">
              <Trash2 :size="16" aria-hidden="true" />
              删除
            </button>
            <button v-else type="button" @click="openReport(comment)">
              <Flag :size="16" aria-hidden="true" />
              举报
            </button>
          </div>

          <div v-if="expandedReplies.has(comment.id)" class="comment-replies">
            <p v-if="replyLoading.has(comment.id)" class="comment-reply-status">正在加载回复…</p>
            <article v-for="reply in replies[comment.id] || []" :key="reply.id" class="comment-reply">
              <RouterLink class="comment-avatar is-small" :to="`/users/${reply.userId}`">
                <img v-if="reply.userAvatarUrl" :src="resolveMediaUrl(reply.userAvatarUrl)" alt="" />
                <UserRound v-else :size="17" aria-hidden="true" />
              </RouterLink>
              <div>
                <header>
                  <RouterLink :to="`/users/${reply.userId}`">{{ reply.userNickname }}</RouterLink>
                  <span v-if="reply.replyToNickname">回复 {{ reply.replyToNickname }}</span>
                  <time :datetime="reply.createdAt">{{ relativeTime(reply.createdAt) }}</time>
                </header>
                <p>{{ reply.content }}</p>
                <div class="comment-actions">
                  <button
                    type="button"
                    :class="{ active: reply.liked }"
                    :disabled="pendingLikes.has(reply.id)"
                    @click="toggleLike(reply)"
                  >
                    <Heart :size="15" :fill="reply.liked ? 'currentColor' : 'none'" aria-hidden="true" />
                    <span>{{ reply.likeCount || "点赞" }}</span>
                  </button>
                  <button type="button" @click="openReply(comment, reply)">回复</button>
                  <button v-if="reply.mine" type="button" class="danger" @click="removeComment(reply, comment)">
                    删除
                  </button>
                  <button v-else type="button" @click="openReport(reply)">举报</button>
                </div>
              </div>
            </article>
            <p v-if="!replyLoading.has(comment.id) && !(replies[comment.id] || []).length" class="comment-reply-status">
              还没有回复
            </p>
          </div>

          <form
            v-if="replyingTo?.root.id === comment.id"
            class="comment-reply-composer"
            @submit.prevent="submitReply"
          >
            <div>
              <strong>回复 {{ replyingTo.target.userNickname }}</strong>
              <button type="button" aria-label="关闭回复" @click="closeReply"><X :size="18" /></button>
            </div>
            <label>
              <span class="sr-only">回复内容</span>
              <textarea
                ref="replyTextarea"
                v-model="replyDraft"
                maxlength="500"
                rows="3"
                :placeholder="`回复 ${replyingTo.target.userNickname}`"
                :disabled="postingReply"
              />
            </label>
            <footer>
              <small>{{ replyDraft.length }}/500</small>
              <button class="primary-action" type="submit" :disabled="postingReply || !replyDraft.trim()">
                {{ postingReply ? "发送中…" : "发送回复" }}
              </button>
            </footer>
          </form>
        </div>
      </article>

      <nav v-if="pageCount > 1" class="search-pagination" aria-label="评论分页">
        <button type="button" :disabled="page <= 1" @click="setPage(page - 1)">上一页</button>
        <span aria-live="polite">第 {{ page }} / {{ pageCount }} 页</span>
        <button type="button" :disabled="page >= pageCount" @click="setPage(page + 1)">下一页</button>
      </nav>
    </div>
    <div v-else class="comment-empty">
      <MessageCircle :size="24" aria-hidden="true" />
      <strong>还没有评论</strong>
      <span>来留下第一条真诚的音乐感受吧。</span>
    </div>

    <Teleport to="body">
      <div v-if="reportTarget" class="comment-sheet-backdrop" @click.self="closeReport" @keydown.esc="closeReport">
        <section
          ref="reportPanel"
          class="comment-report-sheet"
          role="dialog"
          aria-modal="true"
          aria-labelledby="comment-report-title"
          tabindex="-1"
        >
          <header>
            <div>
              <p class="comment-eyebrow">社区安全</p>
              <h2 id="comment-report-title">举报评论</h2>
            </div>
            <button type="button" aria-label="关闭举报面板" @click="closeReport"><X :size="19" /></button>
          </header>
          <p class="comment-report-preview">{{ reportTarget.content }}</p>
          <label>
            <span>举报原因</span>
            <select v-model="reportReason">
              <option value="SPAM">垃圾广告</option>
              <option value="ABUSE">辱骂或攻击</option>
              <option value="HARASSMENT">骚扰</option>
              <option value="COPYRIGHT">侵权内容</option>
              <option value="OTHER">其他</option>
            </select>
          </label>
          <label>
            <span>补充说明（选填）</span>
            <textarea v-model="reportDetail" maxlength="500" rows="4" placeholder="请提供有助于管理员判断的信息" />
          </label>
          <button class="primary-action" type="button" :disabled="reporting" @click="submitReport">
            {{ reporting ? "提交中…" : "提交举报" }}
          </button>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ArrowRight,
  Flag,
  Heart,
  MessageCircle,
  MessagesSquare,
  Pin,
  Trash2,
  UserRound,
  X
} from "lucide-vue-next";
import { commentApi } from "@/api";
import type { CommentItem } from "@/api/types";
import { useAuthStore } from "@/stores/auth";
import { useUiStore } from "@/stores/ui";
import { resolveMediaUrl } from "@/utils/format";

const props = defineProps<{
  targetType: "SONG" | "PLAYLIST";
  targetId: number;
}>();

const auth = useAuthStore();
const ui = useUiStore();
const route = useRoute();
const router = useRouter();
const comments = ref<CommentItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const sort = ref<"LATEST" | "HOT">("LATEST");
const loading = ref(false);
const errorMessage = ref("");
const rootDraft = ref("");
const postingRoot = ref(false);
const replyingTo = ref<{ root: CommentItem; target: CommentItem } | null>(null);
const replyDraft = ref("");
const postingReply = ref(false);
const replyTextarea = ref<HTMLTextAreaElement | null>(null);
const replies = reactive<Record<number, CommentItem[]>>({});
const expandedReplies = reactive(new Set<number>());
const replyLoading = reactive(new Set<number>());
const pendingLikes = reactive(new Set<number>());
const reportTarget = ref<CommentItem | null>(null);
const reportReason = ref("SPAM");
const reportDetail = ref("");
const reporting = ref(false);
const reportPanel = ref<HTMLElement | null>(null);
let reportTrigger: HTMLElement | null = null;
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

watch(
  () => [props.targetType, props.targetId],
  () => {
    page.value = 1;
    resetThread();
    void loadComments();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  reportTrigger = null;
});

async function loadComments() {
  if (!Number.isSafeInteger(props.targetId) || props.targetId <= 0) return;
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await commentApi.list(props.targetType, props.targetId, sort.value, page.value, pageSize);
    comments.value = result.items;
    total.value = result.total;
  } catch {
    errorMessage.value = "评论暂时加载失败，请稍后重试。";
  } finally {
    loading.value = false;
  }
}

function resetThread() {
  comments.value = [];
  total.value = 0;
  expandedReplies.clear();
  Object.keys(replies).forEach((key) => delete replies[Number(key)]);
  closeReply();
}

function setSort(value: "LATEST" | "HOT") {
  if (sort.value === value) return;
  sort.value = value;
  page.value = 1;
  void loadComments();
}

function setPage(value: number) {
  const nextPage = Math.min(Math.max(value, 1), pageCount.value);
  if (nextPage === page.value) return;
  page.value = nextPage;
  void loadComments();
}

async function submitRootComment() {
  const content = rootDraft.value.trim();
  if (!content || postingRoot.value) return;
  postingRoot.value = true;
  try {
    await commentApi.create(props.targetType, props.targetId, content);
    rootDraft.value = "";
    page.value = 1;
    sort.value = "LATEST";
    await loadComments();
    ui.toast("评论已发布");
  } finally {
    postingRoot.value = false;
  }
}

function openReply(root: CommentItem, target: CommentItem) {
  if (!requireAuth()) return;
  replyingTo.value = { root, target };
  void nextTick(() => replyTextarea.value?.focus());
}

function closeReply() {
  replyingTo.value = null;
}

async function submitReply() {
  const context = replyingTo.value;
  const content = replyDraft.value.trim();
  if (!context || !content || postingReply.value) return;
  postingReply.value = true;
  try {
    await commentApi.create(
      props.targetType,
      props.targetId,
      content,
      context.root.id,
      context.target.userId
    );
    replyDraft.value = "";
    replyingTo.value = null;
    expandedReplies.add(context.root.id);
    await loadReplies(context.root);
    ui.toast("回复已发送");
  } finally {
    postingReply.value = false;
  }
}

async function toggleReplies(comment: CommentItem) {
  if (expandedReplies.has(comment.id)) {
    expandedReplies.delete(comment.id);
    return;
  }
  expandedReplies.add(comment.id);
  if (!replies[comment.id]) await loadReplies(comment);
}

async function loadReplies(comment: CommentItem) {
  if (replyLoading.has(comment.id)) return;
  replyLoading.add(comment.id);
  try {
    const result = await commentApi.replies(comment.id, 1, 100);
    replies[comment.id] = result.items;
    comment.replyCount = result.total;
  } finally {
    replyLoading.delete(comment.id);
  }
}

async function toggleLike(comment: CommentItem) {
  if (!requireAuth() || pendingLikes.has(comment.id)) return;
  pendingLikes.add(comment.id);
  try {
    const updated = comment.liked
      ? await commentApi.unlike(comment.id)
      : await commentApi.like(comment.id);
    replaceComment(updated);
  } finally {
    pendingLikes.delete(comment.id);
  }
}

function replaceComment(updated: CommentItem) {
  const rootIndex = comments.value.findIndex((item) => item.id === updated.id);
  if (rootIndex >= 0) comments.value[rootIndex] = updated;
  Object.values(replies).forEach((items) => {
    const replyIndex = items.findIndex((item) => item.id === updated.id);
    if (replyIndex >= 0) items[replyIndex] = updated;
  });
}

async function removeComment(comment: CommentItem, root?: CommentItem) {
  if (!window.confirm("确定删除这条评论吗？删除后无法自行恢复。")) return;
  await commentApi.remove(comment.id);
  if (root) {
    replies[root.id] = (replies[root.id] || []).filter((item) => item.id !== comment.id);
    root.replyCount = Math.max(0, root.replyCount - 1);
  } else if (comment.replyCount > 0) {
    comment.deleted = true;
    comment.content = "该评论已删除";
  } else {
    comments.value = comments.value.filter((item) => item.id !== comment.id);
    total.value = Math.max(0, total.value - 1);
  }
  ui.toast("评论已删除");
}

function openReport(comment: CommentItem) {
  if (!requireAuth()) return;
  reportTrigger = document.activeElement as HTMLElement | null;
  reportTarget.value = comment;
  reportReason.value = "SPAM";
  reportDetail.value = "";
  void nextTick(() => reportPanel.value?.focus());
}

function closeReport() {
  reportTarget.value = null;
  void nextTick(() => reportTrigger?.focus());
}

async function submitReport() {
  if (!reportTarget.value || reporting.value) return;
  reporting.value = true;
  try {
    await commentApi.report(reportTarget.value.id, reportReason.value, reportDetail.value.trim() || undefined);
    closeReport();
    ui.toast("举报已提交，感谢你维护社区氛围");
  } finally {
    reporting.value = false;
  }
}

function requireAuth() {
  if (auth.isAuthenticated) return true;
  goToLogin();
  return false;
}

function goToLogin() {
  void router.push({ name: "login", query: { redirect: route.fullPath } });
}

function relativeTime(value: string) {
  const timestamp = new Date(value).getTime();
  const seconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
  if (seconds < 60) return "刚刚";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "short", day: "numeric" })
    .format(new Date(value));
}
</script>
