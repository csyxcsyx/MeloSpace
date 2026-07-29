import type { Playlist } from "@/api/types";

export function canManagePlaylist(playlist: Pick<Playlist, "canManage" | "userId">, currentUserId?: number | null) {
  return playlist.canManage || (currentUserId != null && playlist.userId === currentUserId);
}

export function applyPlaylistFavorite<T extends Pick<Playlist, "favorited" | "favoriteCount">>(
  playlist: T,
  favorited: boolean
): T {
  if (playlist.favorited === favorited) return playlist;
  return {
    ...playlist,
    favorited,
    favoriteCount: Math.max(0, playlist.favoriteCount + (favorited ? 1 : -1))
  };
}
