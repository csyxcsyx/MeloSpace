import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Song } from "@/api/types";
import LyricPanel from "@/components/LyricPanel.vue";

const lyricSong: Song = {
  id: 901,
  title: "性能测试歌曲",
  artistId: 1,
  artistName: "MeloSpace",
  albumId: null,
  albumTitle: null,
  coverUrl: null,
  audioUrl: "/media/test.mp3",
  lyricUrl: "/media/test-performance.lrc",
  durationSeconds: 180,
  language: "zh",
  genre: "Pop",
  mood: "平静",
  playCount: 0,
  status: 1,
  createdAt: "2026-07-31T00:00:00",
  updatedAt: "2026-07-31T00:00:00"
};

describe("LyricPanel performance behavior", () => {
  const scrollTo = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve("[00:00.00]第一句歌词\n[00:05.00]第二句歌词")
    }));
    Object.defineProperty(HTMLElement.prototype, "scrollTo", {
      configurable: true,
      value: scrollTo
    });
    vi.spyOn(window, "requestAnimationFrame").mockImplementation((callback) => {
      return window.setTimeout(() => callback(0), 0);
    });
  });

  afterEach(() => {
    scrollTo.mockClear();
    Reflect.deleteProperty(HTMLElement.prototype, "scrollTo");
    vi.unstubAllGlobals();
  });

  it("只在当前歌词行变化时重新居中，不随每次播放时间更新强制滚动", async () => {
    const wrapper = mount(LyricPanel, {
      props: {
        song: lyricSong,
        currentTime: 1,
        isCurrentSong: true,
        fullscreen: true
      }
    });
    await flushPromises();
    await waitForAnimationFrame();

    const initialScrollCount = scrollTo.mock.calls.length;
    expect(initialScrollCount).toBeGreaterThan(0);

    await wrapper.setProps({ currentTime: 2 });
    await flushPromises();
    expect(scrollTo).toHaveBeenCalledTimes(initialScrollCount);

    await wrapper.setProps({ currentTime: 6 });
    await flushPromises();
    await waitForAnimationFrame();
    expect(scrollTo.mock.calls.length).toBeGreaterThan(initialScrollCount);
  });
});

function waitForAnimationFrame() {
  return new Promise<void>((resolve) => window.setTimeout(resolve, 0));
}
