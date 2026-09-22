import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import ToastHost from "@/components/ToastHost.vue";
import { useUiStore } from "@/stores/ui";

describe("ToastHost", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("在统一状态浮层中展示并允许关闭提示", async () => {
    const ui = useUiStore();
    ui.toast("请先登录后播放音乐");
    const wrapper = mount(ToastHost);

    expect(wrapper.get(".toast-host").attributes("role")).toBe("status");
    expect(wrapper.get(".toast").text()).toBe("请先登录后播放音乐");

    await wrapper.get(".toast").trigger("click");
    expect(ui.toasts).toHaveLength(0);
  });
});
