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
  let cleanupWrapper: (() => void) | null = null;

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
    vi.useRealTimers();
    cleanupWrapper?.();
    cleanupWrapper = null;
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
    cleanupWrapper = () => wrapper.unmount();
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

  it("为逐字歌词输出连续的百分比进度", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve("[00:00.00]<00:01.00>逐<00:02.00>字<00:03.00>歌词")
    }));
    const wrapper = mount(LyricPanel, {
      props: {
        song: { ...lyricSong, lyricUrl: "/media/test-word-timed.lrc" },
        currentTime: 2.36,
        isCurrentSong: true,
        fullscreen: true
      }
    });
    cleanupWrapper = () => wrapper.unmount();
    await flushPromises();

    const words = wrapper.findAll(".lyric-word");
    expect(words).toHaveLength(3);
    expect(words[0].attributes("style")).toContain("100.00%");
    expect(words[1].attributes("style")).toContain("50.00%");
    expect(words[2].attributes("style")).toContain("0.00%");
  });

  it("快节奏逐字歌词只推进当前字，不与相邻字重叠", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve("[00:00.00]<00:01.00>快<00:01.05>速<00:01.10>推进")
    }));
    const wrapper = mount(LyricPanel, {
      props: {
        song: { ...lyricSong, lyricUrl: "/media/test-fast-word-timed.lrc" },
        currentTime: 0.94,
        isCurrentSong: true,
        fullscreen: true
      }
    });
    cleanupWrapper = () => wrapper.unmount();
    await flushPromises();

    const words = wrapper.findAll(".lyric-word");
    expect(words).toHaveLength(3);
    expect(words[0].attributes("style")).toContain("100.00%");
    expect(words[1].attributes("style")).toContain("60.00%");
    expect(words[2].attributes("style")).toContain("0.00%");
    expect(wrapper.findAll(".lyric-word-progressing")).toHaveLength(1);
    expect(words[1].classes()).toContain("lyric-word-progressing");
  });

  it("在预览模式只显示当前附近三行并可请求打开完整歌词", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve([
        "[00:00.00]第一句歌词",
        "[00:05.00]第二句歌词",
        "[00:10.00]第三句歌词",
        "[00:15.00]第四句歌词"
      ].join("\n"))
    }));
    const wrapper = mount(LyricPanel, {
      props: {
        song: { ...lyricSong, lyricUrl: "/media/test-preview.lrc" },
        currentTime: 6,
        isCurrentSong: true,
        preview: true
      }
    });
    cleanupWrapper = () => wrapper.unmount();
    await flushPromises();

    const previewLines = wrapper.findAll(".lyric-preview-line");
    expect(previewLines).toHaveLength(3);
    expect(previewLines[1].classes()).toContain("active");
    expect(wrapper.find(".lyric-scroll").exists()).toBe(false);

    await previewLines[1].trigger("click");
    expect(wrapper.emitted("activate")).toHaveLength(1);
    expect(wrapper.emitted("seek")).toBeUndefined();
  });

  it("手动浏览时不显示回正按钮，并在静止后自动回到当前歌词", async () => {
    vi.useFakeTimers();
    const wrapper = mount(LyricPanel, {
      props: {
        song: lyricSong,
        currentTime: 6,
        isCurrentSong: true,
        fullscreen: true
      }
    });
    cleanupWrapper = () => wrapper.unmount();
    await flushPromises();
    await vi.runOnlyPendingTimersAsync();
    scrollTo.mockClear();

    await wrapper.get(".lyric-scroll").trigger("touchmove");
    expect(wrapper.find(".lyric-follow-button").exists()).toBe(false);

    await vi.advanceTimersByTimeAsync(4199);
    expect(scrollTo).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    await vi.runOnlyPendingTimersAsync();
    expect(scrollTo).toHaveBeenCalled();
    vi.useRealTimers();
  });
});

function waitForAnimationFrame() {
  return new Promise<void>((resolve) => window.setTimeout(resolve, 0));
}
