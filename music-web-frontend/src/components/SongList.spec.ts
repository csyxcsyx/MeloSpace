import { mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { defineComponent, type PropType } from "vue";
import { describe, expect, it } from "vitest";
import type { Song } from "@/api/types";
import SongList from "@/components/SongList.vue";

const SongRowStub = defineComponent({
  name: "SongRow",
  props: {
    song: { type: Object as PropType<Song>, required: true }
  },
  emits: ["togglePlay", "openPlayer", "favoriteChange"],
  template: `
    <article class="song-row-stub" :data-song-id="song.id">
      <slot name="leading" :song="song" />
      <slot name="meta" :song="song" />
      <button class="toggle" type="button" @click="$emit('togglePlay', song)">播放</button>
    </article>
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
    language: "中文",
    genre: "Pop",
    mood: "MeloSpace",
    playCount: 0,
    status: 1,
    createdAt: "2026-07-28T00:00:00",
    updatedAt: "2026-07-28T00:00:00"
  };
}

describe("SongList", () => {
  it("preserves source order, forwards scoped slots, and relays playback", async () => {
    const songs = [song(3), song(1), song(2)];
    const wrapper = mount(SongList, {
      props: { songs },
      slots: {
        leading: `<template #leading="{ index }"><span class="leading-slot">{{ index + 1 }}</span></template>`,
        meta: `<template #meta="{ song }"><span class="meta-slot">来源 {{ song.id }}</span></template>`
      },
      global: {
        plugins: [createPinia()],
        stubs: { SongRow: SongRowStub }
      }
    });

    const rows = wrapper.findAll(".song-row-stub");
    expect(wrapper.get(".song-list-header-identity").text()).toBe("歌名 / 歌手");
    expect(wrapper.get(".song-list-header-meta").text()).toContain("专辑");
    expect(wrapper.get(".song-list-header-meta").text()).toContain("时长");
    expect(rows.map((row) => Number(row.attributes("data-song-id")))).toEqual([3, 1, 2]);
    expect(wrapper.findAll(".leading-slot").map((item) => item.text())).toEqual(["1", "2", "3"]);
    expect(wrapper.findAll(".meta-slot").map((item) => item.text())).toEqual(["来源 3", "来源 1", "来源 2"]);

    await rows[1].get(".toggle").trigger("click");
    expect(wrapper.emitted("togglePlay")?.[0]).toEqual([songs[1]]);
  });

  it("can hide the shared column heading in compact contexts", () => {
    const wrapper = mount(SongList, {
      props: { songs: [song(1)], showHeader: false },
      global: {
        plugins: [createPinia()],
        stubs: { SongRow: SongRowStub }
      }
    });

    expect(wrapper.find(".song-list-header").exists()).toBe(false);
  });
});
