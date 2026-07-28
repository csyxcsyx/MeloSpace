import { mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { defineComponent, type PropType } from "vue";
import { beforeEach, describe, expect, it } from "vitest";
import type { Song } from "@/api/types";
import SongColumnList from "@/components/SongColumnList.vue";

const SongRowStub = defineComponent({
  name: "SongRow",
  props: {
    song: {
      type: Object as PropType<Song>,
      required: true
    }
  },
  emits: ["togglePlay", "openPlayer", "more"],
  template: `
    <button
      class="song-row-stub"
      type="button"
      :data-song-id="song.id"
      @click="$emit('togglePlay', song)"
    >
      {{ song.title }}
    </button>
  `
});

function song(id: number): Song {
  return {
    id,
    title: `歌曲 ${id}`,
    artistId: id,
    artistName: `歌手 ${id}`,
    albumId: null,
    albumTitle: null,
    coverUrl: null,
    audioUrl: `/media/${id}.mp3`,
    lyricUrl: null,
    durationSeconds: 180,
    language: "zh",
    genre: "Pop",
    mood: "MeloSpace",
    playCount: 0,
    status: 1,
    createdAt: "2026-07-28T00:00:00",
    updatedAt: "2026-07-28T00:00:00"
  };
}

describe("SongColumnList", () => {
  beforeEach(() => {
    localStorage.clear();
    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      value: 390
    });
  });

  it("在手机单列布局中保持歌曲与播放队列的源顺序一致", async () => {
    const songs = [song(1), song(2), song(3), song(4), song(5), song(6)];
    const wrapper = mount(SongColumnList, {
      props: {
        songs,
        columnCount: 3
      },
      global: {
        plugins: [createPinia()],
        stubs: {
          SongRow: SongRowStub
        }
      }
    });

    const renderedRows = wrapper.findAll(".song-row-stub");
    expect(renderedRows.map((row) => Number(row.attributes("data-song-id")))).toEqual(songs.map(({ id }) => id));

    await renderedRows[3].trigger("click");
    expect((wrapper.emitted("togglePlay")?.[0]?.[0] as Song).id).toBe(songs[3].id);
  });
});
