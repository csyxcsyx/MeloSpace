import { mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it } from "vitest";
import type { Song } from "@/api/types";
import SongActionsMenu from "@/components/SongActionsMenu.vue";

const song: Song = {
  id: 8,
  title: "夜曲",
  artistId: 1,
  artistName: "周杰伦",
  albumId: 2,
  albumTitle: "十一月的萧邦",
  coverUrl: null,
  audioUrl: "/media/night.mp3",
  lyricUrl: null,
  durationSeconds: 226,
  language: "中文",
  genre: "Pop",
  mood: "安静",
  playCount: 20,
  status: 1,
  createdAt: "2026-07-28T00:00:00",
  updatedAt: "2026-07-28T00:00:00"
};

async function mountMenu(variant: "row" | "player" = "row") {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: { template: "<div />" } },
      { path: "/login", name: "login", component: { template: "<div />" } },
      { path: "/songs/:id", name: "song-detail", component: { template: "<div />" } }
    ]
  });
  await router.push("/");
  await router.isReady();
  return mount(SongActionsMenu, {
    props: { song, variant, favorited: true },
    global: { plugins: [createPinia(), router] }
  });
}

describe("SongActionsMenu", () => {
  it("renders favorite, download and playlist shortcuts before the more trigger for rows", async () => {
    const wrapper = await mountMenu();
    const shortcuts = wrapper.findAll("[data-quick-action]");

    expect(shortcuts.map((button) => button.attributes("data-quick-action"))).toEqual([
      "favorite",
      "download",
      "playlist"
    ]);
    expect(shortcuts.map((button) => button.attributes("aria-label"))).toEqual([
      "取消收藏：夜曲",
      "下载：夜曲",
      "添加到歌单：夜曲"
    ]);
    expect(wrapper.element.lastElementChild?.classList.contains("song-actions-trigger")).toBe(true);
  });

  it("keeps the player variant compact", async () => {
    const wrapper = await mountMenu("player");

    expect(wrapper.find(".song-actions-quick").exists()).toBe(false);
    expect(wrapper.findAll(".song-actions-trigger")).toHaveLength(1);
  });
});
