import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import PageHeader from "@/components/PageHeader.vue";

describe("PageHeader", () => {
  it("renders a single primary heading with eyebrow and description", () => {
    const wrapper = mount(PageHeader, {
      props: {
        eyebrow: "个人中心",
        title: "我的音乐",
        description: "管理你的歌单、收藏与最近播放。"
      }
    });

    expect(wrapper.findAll("h1")).toHaveLength(1);
    expect(wrapper.get("h1").text()).toBe("我的音乐");
    expect(wrapper.get(".page-header-eyebrow").text()).toBe("个人中心");
    expect(wrapper.get(".page-header-description").text()).toContain("歌单");
    expect(wrapper.get("header").attributes("aria-labelledby")).toBe(wrapper.get("h1").attributes("id"));
  });

  it("renders summary and action slots in a stable right-side region", () => {
    const wrapper = mount(PageHeader, {
      props: { title: "歌曲库" },
      slots: {
        summary: `<span data-slot="summary">488 首</span>`,
        actions: `<button data-slot="actions">刷新</button>`
      }
    });

    expect(wrapper.get(".page-header-summary [data-slot='summary']").text()).toBe("488 首");
    expect(wrapper.get(".page-header-actions [data-slot='actions']").text()).toBe("刷新");
  });
});
