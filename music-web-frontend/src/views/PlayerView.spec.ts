import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { createPinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Song } from "@/api/types";
import { usePlayerStore } from "@/stores/player";
import PlayerView from "@/views/PlayerView.vue";

const song: Song = {
  id: 101,
  title: "晴天",
  artistId: 1,
  artistName: "周杰伦",
  albumId: 11,
  albumTitle: "叶惠美",
  coverUrl: null,
  audioUrl: "/media/sunny.mp3",
  lyricUrl: "/media/sunny.lrc",
  durationSeconds: 269,
  language: "中文",
  genre: "Pop",
  mood: "怀旧",
  playCount: 88,
  status: 1,
  createdAt: "2026-09-22T00:00:00",
  updatedAt: "2026-09-22T00:00:00"
};

describe("PlayerView mobile pager", () => {
  let wrapper: VueWrapper | null = null;
  const scrollTo = vi.fn(function (this: HTMLElement, options: ScrollToOptions) {
    this.scrollLeft = Number(options.left ?? 0);
  });

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem("melospace-player-song", JSON.stringify(song));
    localStorage.setItem("melospace-player-queue", JSON.stringify([song]));
    vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn()
    }));
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve("[00:00.00]第一句歌词\n[00:05.00]第二句歌词\n[00:10.00]第三句歌词")
    }));
    Object.defineProperty(HTMLElement.prototype, "scrollTo", {
      configurable: true,
      value: scrollTo
    });
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    localStorage.clear();
    scrollTo.mockClear();
    Reflect.deleteProperty(HTMLElement.prototype, "scrollTo");
    vi.unstubAllGlobals();
  });

  it("starts on the cover page and exposes an accessible lyrics page control", async () => {
    wrapper = await mountPlayer();
    const tabs = wrapper.findAll('[role="tab"]');

    expect(tabs).toHaveLength(2);
    expect(tabs[0].attributes("aria-selected")).toBe("true");
    expect(wrapper.get("#player-cover-page").attributes("aria-hidden")).toBe("false");
    expect(wrapper.get("#player-lyrics-page").attributes("aria-hidden")).toBe("true");
  });

  it("moves to lyrics from the page control and resets after a song change", async () => {
    wrapper = await mountPlayer();
    const stage = wrapper.get(".player-stage");
    Object.defineProperty(stage.element, "clientWidth", { configurable: true, value: 360 });

    await wrapper.findAll('[role="tab"]')[1].trigger("click");
    expect(scrollTo).toHaveBeenLastCalledWith({ left: 360, behavior: "smooth" });
    expect(wrapper.findAll('[role="tab"]')[1].attributes("aria-selected")).toBe("true");

    const player = usePlayerStore();
    player.replaceCurrentSong({ ...song, id: 102, title: "夜曲" }, [{ ...song, id: 102, title: "夜曲" }]);
    await flushPromises();

    expect(wrapper.findAll('[role="tab"]')[0].attributes("aria-selected")).toBe("true");
    expect(scrollTo).toHaveBeenLastCalledWith({ left: 0, behavior: "auto" });
  });

  it("updates the selected page after a native horizontal swipe settles", async () => {
    wrapper = await mountPlayer();
    const stage = wrapper.get(".player-stage");
    Object.defineProperty(stage.element, "clientWidth", { configurable: true, value: 360 });
    Object.defineProperty(stage.element, "scrollLeft", { configurable: true, writable: true, value: 360 });

    await stage.trigger("scroll");
    await waitForAnimationFrame();

    expect(wrapper.findAll('[role="tab"]')[1].attributes("aria-selected")).toBe("true");
    expect(wrapper.get("#player-cover-page").attributes("aria-hidden")).toBe("true");
    expect(wrapper.get("#player-lyrics-page").attributes("aria-hidden")).toBe("false");
  });

  it("opens lyrics from the cover preview and accepts a right swipe from inside lyrics", async () => {
    wrapper = await mountPlayer();
    const stage = wrapper.get(".player-stage");
    Object.defineProperty(stage.element, "clientWidth", { configurable: true, value: 360 });

    await flushPromises();
    await wrapper.get(".player-cover-lyrics .lyric-preview-line").trigger("click");
    expect(wrapper.findAll('[role="tab"]')[1].attributes("aria-selected")).toBe("true");
    expect(scrollTo).toHaveBeenLastCalledWith({ left: 360, behavior: "smooth" });

    await stage.trigger("touchstart", { touches: [{ clientX: 72, clientY: 420 }] });
    await stage.trigger("touchend", { changedTouches: [{ clientX: 286, clientY: 426 }] });

    expect(wrapper.findAll('[role="tab"]')[0].attributes("aria-selected")).toBe("true");
    expect(scrollTo).toHaveBeenLastCalledWith({ left: 0, behavior: "smooth" });
  });

  it("does not change pages for a vertical lyric scroll gesture", async () => {
    wrapper = await mountPlayer();
    const stage = wrapper.get(".player-stage");
    Object.defineProperty(stage.element, "clientWidth", { configurable: true, value: 360 });
    await wrapper.findAll('[role="tab"]')[1].trigger("click");
    scrollTo.mockClear();

    await stage.trigger("touchstart", { touches: [{ clientX: 190, clientY: 610 }] });
    await stage.trigger("touchend", { changedTouches: [{ clientX: 202, clientY: 280 }] });

    expect(wrapper.findAll('[role="tab"]')[1].attributes("aria-selected")).toBe("true");
    expect(scrollTo).not.toHaveBeenCalled();
  });
});

async function mountPlayer() {
  const pinia = createPinia();
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/player", component: { template: "<div />" } },
      { path: "/discover", component: { template: "<div />" } }
    ]
  });
  await router.push("/player");
  await router.isReady();

  const mounted = mount(PlayerView, {
    global: {
      plugins: [pinia, router],
      stubs: {
        SongActionsMenu: { template: '<button class="song-actions-stub">更多</button>' }
      }
    }
  });
  await flushPromises();
  return mounted;
}

function waitForAnimationFrame() {
  return new Promise<void>((resolve) => window.setTimeout(resolve, 24));
}
