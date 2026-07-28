import { createPinia, setActivePinia } from "pinia";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import CommentThread from "@/components/CommentThread.vue";

const { createComment, listComments, pushRoute } = vi.hoisted(() => ({
  createComment: vi.fn(),
  listComments: vi.fn(),
  pushRoute: vi.fn()
}));

vi.mock("@/api", () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn()
  },
  userApi: {
    me: vi.fn(),
    deleteMe: vi.fn()
  },
  commentApi: {
    list: listComments,
    create: createComment,
    replies: vi.fn(),
    like: vi.fn(),
    unlike: vi.fn(),
    remove: vi.fn(),
    report: vi.fn()
  }
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({ fullPath: "/songs/1" }),
  useRouter: () => ({ push: pushRoute })
}));

describe("CommentThread", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    localStorage.setItem("music-web-token", "test-token");
    localStorage.setItem("music-web-user", JSON.stringify({
      id: 2,
      username: "demo",
      nickname: "演示用户",
      avatarUrl: null,
      role: "USER"
    }));
    setActivePinia(createPinia());
    listComments.mockResolvedValue({ items: [], page: 1, size: 20, total: 0 });
    createComment.mockImplementation(() => new Promise(() => undefined));
  });

  it("发布请求进行中时阻止重复提交并保留草稿", async () => {
    const wrapper = shallowMount(CommentThread, {
      props: { targetType: "SONG", targetId: 1 },
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
          Teleport: true
        }
      }
    });
    await flushPromises();

    const textarea = wrapper.get(".comment-composer textarea");
    await textarea.setValue("不会重复发布的评论");
    const form = wrapper.get(".comment-composer");
    await form.trigger("submit");
    await form.trigger("submit");

    expect(createComment).toHaveBeenCalledTimes(1);
    expect(createComment).toHaveBeenCalledWith("SONG", 1, "不会重复发布的评论");
    expect((textarea.element as HTMLTextAreaElement).value).toBe("不会重复发布的评论");
  });
});
