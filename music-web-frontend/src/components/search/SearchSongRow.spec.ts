import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import type { Song } from "@/api/types";
import SearchSongRow from "@/components/search/SearchSongRow.vue";

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

describe("SearchSongRow", () => {
  it("provides plain mobile artist and album metadata", () => {
    const wrapper = mount(SearchSongRow, {
      props: { song, keyword: "夜曲" },
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
          SongActionsMenu: { template: "<button>更多</button>" }
        }
      }
    });

    const mobileSubtitle = wrapper.get(".search-song-subtitle-mobile");
    expect(mobileSubtitle.element.tagName).toBe("SPAN");
    expect(mobileSubtitle.text()).toBe("周杰伦 · 十一月的萧邦");
    expect(mobileSubtitle.attributes("href")).toBeUndefined();
  });
});
