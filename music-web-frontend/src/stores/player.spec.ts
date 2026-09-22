import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Song } from "@/api/types";
import { usePlayerStore } from "@/stores/player";
import { useUiStore } from "@/stores/ui";

const song = {
  id: 1,
  title: "晴天",
  artistId: 1,
  artistName: "周杰伦",
  albumId: 1,
  albumTitle: "叶惠美",
  durationSeconds: 269,
  audioUrl: "/media/audio/sunny.mp3",
  coverUrl: "/media/cover/sunny.jpg"
} as Song;

describe("player authentication guard", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it("未登录时保留可浏览歌曲但拒绝播放并显示提示", async () => {
    const player = usePlayerStore();
    const ui = useUiStore();

    await expect(player.playSong(song, [song])).resolves.toBe(false);

    expect(player.currentSong).toBeNull();
    expect(player.queue).toEqual([]);
    expect(player.isPlaying).toBe(false);
    expect(player.errorMessage).toBe("请先登录后播放音乐");
    expect(ui.toasts.map((item) => item.message)).toEqual(["请先登录后播放音乐"]);
  });
});
