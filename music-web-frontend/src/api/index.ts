import { http, unwrap } from "@/api/http";
import type {
  Album,
  Artist,
  AuthResponse,
  CommentItem,
  FavoriteItem,
  PageResult,
  PlayHistoryItem,
  Playlist,
  PlaylistDetail,
  SearchResponse,
  Song,
  UploadFile,
  UserSummary
} from "@/api/types";

export const authApi = {
  login: (username: string, password: string) =>
    unwrap<AuthResponse>(http.post("/api/auth/login", { username, password })),
  register: (username: string, password: string, nickname?: string) =>
    unwrap<AuthResponse>(http.post("/api/auth/register", { username, password, nickname })),
  logout: () => unwrap<void>(http.post("/api/auth/logout"))
};

export const userApi = {
  me: () => unwrap<UserSummary>(http.get("/api/users/me")),
  playlists: (page = 1, size = 20) =>
    unwrap<PageResult<Playlist>>(http.get("/api/users/me/playlists", { params: { page, size } })),
  favorites: (page = 1, size = 20) =>
    unwrap<PageResult<FavoriteItem>>(http.get("/api/users/me/favorites", { params: { page, size } })),
  recentPlays: (page = 1, size = 20) =>
    unwrap<PageResult<PlayHistoryItem>>(http.get("/api/users/me/recent-plays", { params: { page, size } }))
};

export const songApi = {
  list: (params: { page?: number; size?: number; keyword?: string; artistId?: number; albumId?: number } = {}) =>
    unwrap<PageResult<Song>>(http.get("/api/songs", { params })),
  detail: (id: number) => unwrap<Song>(http.get(`/api/songs/${id}`)),
  recordPlay: (id: number, progressSeconds = 0, sourceType = "FRONTEND") =>
    unwrap<PlayHistoryItem>(http.post(`/api/songs/${id}/play-record`, { progressSeconds, sourceType }))
};

export const artistApi = {
  list: (params: { page?: number; size?: number; keyword?: string } = {}) =>
    unwrap<Artist[]>(http.get("/api/artists", { params }))
};

export const albumApi = {
  list: (params: { page?: number; size?: number; keyword?: string; artistId?: number } = {}) =>
    unwrap<Album[]>(http.get("/api/albums", { params }))
};

export const playlistApi = {
  list: (params: { page?: number; size?: number; keyword?: string } = {}) =>
    unwrap<PageResult<Playlist>>(http.get("/api/playlists", { params })),
  detail: (id: number) => unwrap<PlaylistDetail>(http.get(`/api/playlists/${id}`)),
  create: (payload: { title: string; description?: string; coverUrl?: string; visibility?: string }) =>
    unwrap<PlaylistDetail>(http.post("/api/playlists", payload)),
  update: (id: number, payload: { title: string; description?: string; coverUrl?: string; visibility?: string }) =>
    unwrap<PlaylistDetail>(http.put(`/api/playlists/${id}`, payload)),
  remove: (id: number) => unwrap<void>(http.delete(`/api/playlists/${id}`)),
  addSong: (id: number, songId: number) => unwrap<PlaylistDetail>(http.post(`/api/playlists/${id}/songs`, { songId })),
  removeSong: (id: number, songId: number) => unwrap<PlaylistDetail>(http.delete(`/api/playlists/${id}/songs/${songId}`)),
  reorder: (id: number, songIds: number[]) => unwrap<PlaylistDetail>(http.put(`/api/playlists/${id}/songs/order`, { songIds }))
};

export const favoriteApi = {
  add: (targetType: "SONG" | "PLAYLIST", targetId: number) =>
    unwrap<FavoriteItem>(http.post("/api/favorites", { targetType, targetId })),
  remove: (targetType: "SONG" | "PLAYLIST", targetId: number) =>
    unwrap<void>(http.delete("/api/favorites", { params: { targetType, targetId } }))
};

export const commentApi = {
  list: (targetType: "SONG" | "PLAYLIST", targetId: number, page = 1, size = 20) =>
    unwrap<PageResult<CommentItem>>(http.get("/api/comments", { params: { targetType, targetId, page, size } })),
  create: (targetType: "SONG" | "PLAYLIST", targetId: number, content: string) =>
    unwrap<CommentItem>(http.post("/api/comments", { targetType, targetId, content })),
  remove: (id: number) => unwrap<void>(http.delete(`/api/comments/${id}`))
};

export const searchApi = {
  all: (keyword: string) => unwrap<SearchResponse>(http.get("/api/search", { params: { keyword } }))
};

export const adminApi = {
  dashboard: () => unwrap<Record<string, number>>(http.get("/api/admin/dashboard")),
  songs: (params: { page?: number; size?: number; keyword?: string; status?: number } = {}) =>
    unwrap<PageResult<Song>>(http.get("/api/admin/songs", { params })),
  createSong: (payload: Partial<Song> & { title: string; artistId: number; audioUrl: string }) =>
    unwrap<Song>(http.post("/api/admin/songs", payload)),
  updateSong: (id: number, payload: Partial<Song> & { title: string; artistId: number; audioUrl: string }) =>
    unwrap<Song>(http.put(`/api/admin/songs/${id}`, payload)),
  updateSongStatus: (id: number, status: number) =>
    unwrap<Song>(http.patch(`/api/admin/songs/${id}/status`, { status })),
  createArtist: (payload: { name: string; bio?: string; avatarUrl?: string }) =>
    unwrap<Artist>(http.post("/api/admin/artists", payload)),
  createAlbum: (payload: { title: string; artistId: number; coverUrl?: string; releaseDate?: string }) =>
    unwrap<Album>(http.post("/api/admin/albums", payload)),
  upload: (file: File, fileType: string) => {
    const form = new FormData();
    form.append("file", file);
    form.append("fileType", fileType);
    return unwrap<UploadFile>(http.post("/api/admin/upload", form));
  }
};
