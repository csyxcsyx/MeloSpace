import { mount } from "@vue/test-utils";
import { defineComponent } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Song } from "@/api/types";
import SongRow from "@/components/SongRow.vue";

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

const SongActionsMenuStub = defineComponent({
  name: "SongActionsMenu",
  props: { favorited: Boolean },
  emits: ["favoriteChange"],
  template: `<button class="song-actions-stub" :data-favorited="favorited" @click="$emit('favoriteChange', false)">更多</button>`
});

function mountRow() {
  return mount(SongRow, {
    props: { song, favorited: true },
    slots: {
      leading: `<span data-part="leading">01</span>`,
      title: `<strong data-part="title">插槽标题</strong>`,
      subtitle: `<span data-part="subtitle">插槽副标题</span>`,
      meta: `<span data-part="meta">播放 20 次</span>`,
      actions: `<button data-part="actions">移除</button>`
    },
    global: {
      stubs: {
        RouterLink: { template: `<a><slot /></a>` },
        SongActionsMenu: SongActionsMenuStub
      }
    }
  });
}

describe("SongRow", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("keeps contextual actions before the three-dot menu and forwards all content slots", () => {
    const wrapper = mountRow();
    const classes = [...wrapper.element.children].map((element) => element.classList[0]);

    expect(classes).toEqual([
      "song-row-identity",
      "song-row-meta",
      "song-row-context-actions",
      "song-actions-stub"
    ]);
    expect([...wrapper.get(".song-row-identity").element.children].map((element) => element.classList[0])).toEqual([
      "song-row-leading",
      "song-cover",
      "song-info"
    ]);
    expect(wrapper.findAll("[data-part]").map((item) => item.attributes("data-part"))).toEqual([
      "leading",
      "title",
      "subtitle",
      "meta",
      "actions"
    ]);
    expect(wrapper.get(".song-actions-stub").attributes("data-favorited")).toBe("true");
  });

  it("emits playback and favorite changes with the source song", async () => {
    const wrapper = mountRow();
    await wrapper.get(".song-cover").trigger("click");
    await wrapper.get(".song-actions-stub").trigger("click");

    expect(wrapper.emitted("togglePlay")?.[0]).toEqual([song]);
    expect(wrapper.emitted("favoriteChange")?.[0]).toEqual([song, false]);
  });

  it("plays from the non-interactive row area on mobile without hijacking controls", async () => {
    vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: true }));
    const wrapper = mountRow();

    await wrapper.get(".song-row").trigger("click");
    await wrapper.get('[data-part="actions"]').trigger("click");

    expect(wrapper.emitted("togglePlay")).toEqual([[song]]);
  });

  it("renders mobile artist and album metadata as plain text", () => {
    const wrapper = mount(SongRow, {
      props: { song },
      global: {
        stubs: {
          RouterLink: { template: `<a><slot /></a>` },
          SongActionsMenu: SongActionsMenuStub
        }
      }
    });

    expect(wrapper.get(".song-subtitle").text()).toContain("周杰伦");
    expect(wrapper.get(".song-artist-mobile").element.tagName).toBe("SPAN");
    expect(wrapper.get(".song-mobile-album").element.tagName).toBe("SPAN");
    expect(wrapper.get(".song-mobile-album").text()).toBe("十一月的萧邦");
  });
});
