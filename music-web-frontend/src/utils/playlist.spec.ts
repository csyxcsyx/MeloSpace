import { describe, expect, it } from "vitest";
import { applyPlaylistFavorite, canManagePlaylist } from "@/utils/playlist";

describe("playlist community helpers", () => {
  it("uses server permission and keeps an owner fallback for older API responses", () => {
    expect(canManagePlaylist({ canManage: true, userId: 8 }, 99)).toBe(true);
    expect(canManagePlaylist({ canManage: false, userId: 8 }, 8)).toBe(true);
    expect(canManagePlaylist({ canManage: false, userId: 8 }, 9)).toBe(false);
    expect(canManagePlaylist({ canManage: false, userId: 8 }, null)).toBe(false);
  });

  it("updates favorite state and count once without allowing a negative count", () => {
    const playlist = { favorited: false, favoriteCount: 2 };
    const favorited = applyPlaylistFavorite(playlist, true);
    expect(favorited).toEqual({ favorited: true, favoriteCount: 3 });
    expect(applyPlaylistFavorite(favorited, true)).toBe(favorited);
    expect(applyPlaylistFavorite({ favorited: true, favoriteCount: 0 }, false)).toEqual({
      favorited: false,
      favoriteCount: 0
    });
  });
});
