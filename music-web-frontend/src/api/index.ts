import { http, unwrap } from "@/api/http";
import type {
  Album,
  AdminUser,
  Artist,
  AuthResponse,
  CommentItem,
  CommentReportItem,
  FavoriteItem,
  LddcLyricResult,
  PageResult,
  PlayHistoryItem,
  Playlist,
  PlaylistDetail,
  PublicUserProfile,
  SearchResponse,
  SearchResultItemMap,
  SearchResultType,
  SearchSuggestion,
  Song,
  UploadFile,
  UserSummary
} from "@/api/types";

const LDDC_IMPORT_TIMEOUT_MS = 190000;

export const authApi = {
  login: (username: string, password: string) =>
    unwrap<AuthResponse>(http.post("/api/auth/login", { username, password })),
  register: (username: string, password: string, nickname?: string) =>
    unwrap<AuthResponse>(http.post("/api/auth/register", { username, password, nickname })),
  logout: () => unwrap<void>(http.post("/api/auth/logout"))
};

export const userApi = {
  me: () => unwrap<UserSummary>(http.get("/api/users/me")),
  publicProfile: (id: number) => unwrap<PublicUserProfile>(http.get(`/api/users/${id}`)),
  publicPlaylists: (id: number, page = 1, size = 20) =>
    unwrap<PageResult<Playlist>>(http.get(`/api/users/${id}/playlists`, { params: { page, size } })),
  updateMe: (payload: { nickname: string; avatarUrl?: string | null; bio?: string | null }) =>
    unwrap<UserSummary>(http.put("/api/users/me", payload)),
  deleteMe: () => unwrap<void>(http.delete("/api/users/me")),
  playlists: (page = 1, size = 20) =>
    unwrap<PageResult<Playlist>>(http.get("/api/users/me/playlists", { params: { page, size } })),
  favorites: (page = 1, size = 20) =>
    unwrap<PageResult<FavoriteItem>>(http.get("/api/users/me/favorites", { params: { page, size } })),
  recentPlays: (page = 1, size = 20) =>
    unwrap<PageResult<PlayHistoryItem>>(http.get("/api/users/me/recent-plays", { params: { page, size } })),
  clearRecentPlays: () => unwrap<void>(http.delete("/api/users/me/recent-plays"))
};

export const songApi = {
  list: (params: { page?: number; size?: number; keyword?: string; artistId?: number; albumId?: number; sort?: string } = {}) =>
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
  create: (payload: { title: string; description?: string; coverUrl?: string; visibility?: string; tags?: string[] }) =>
    unwrap<PlaylistDetail>(http.post("/api/playlists", payload)),
  update: (id: number, payload: { title: string; description?: string; coverUrl?: string; visibility?: string; tags?: string[] }) =>
    unwrap<PlaylistDetail>(http.put(`/api/playlists/${id}`, payload)),
  remove: (id: number) => unwrap<void>(http.delete(`/api/playlists/${id}`)),
  addSong: (id: number, songId: number) => unwrap<PlaylistDetail>(http.post(`/api/playlists/${id}/songs`, { songId })),
  removeSong: (id: number, songId: number) => unwrap<PlaylistDetail>(http.delete(`/api/playlists/${id}/songs/${songId}`)),
  reorder: (id: number, songIds: number[]) => unwrap<PlaylistDetail>(http.put(`/api/playlists/${id}/songs/order`, { songIds })),
  addSongs: (id: number, songIds: number[]) =>
    unwrap<PlaylistDetail>(http.post(`/api/playlists/${id}/songs/batch`, { songIds })),
  removeSongs: (id: number, songIds: number[]) =>
    unwrap<PlaylistDetail>(http.delete(`/api/playlists/${id}/songs/batch`, { data: { songIds } })),
  recordPlay: (id: number) => unwrap<PlaylistDetail>(http.post(`/api/playlists/${id}/play`))
};

export const favoriteApi = {
  add: (targetType: "SONG" | "PLAYLIST", targetId: number) =>
    unwrap<FavoriteItem>(http.post("/api/favorites", { targetType, targetId })),
  remove: (targetType: "SONG" | "PLAYLIST", targetId: number) =>
    unwrap<void>(http.delete("/api/favorites", { params: { targetType, targetId } })),
  statuses: (targetType: "SONG" | "PLAYLIST", targetIds: number[]) =>
    unwrap<Record<string, boolean>>(http.get("/api/favorites/status", {
      params: { targetType, targetIds: targetIds.join(",") }
    }))
};

export const commentApi = {
  list: (targetType: "SONG" | "PLAYLIST", targetId: number, sort: "LATEST" | "HOT" = "LATEST", page = 1, size = 20) =>
    unwrap<PageResult<CommentItem>>(http.get("/api/comments", { params: { targetType, targetId, sort, page, size } })),
  replies: (id: number, page = 1, size = 50) =>
    unwrap<PageResult<CommentItem>>(http.get(`/api/comments/${id}/replies`, { params: { page, size } })),
  create: (
    targetType: "SONG" | "PLAYLIST",
    targetId: number,
    content: string,
    parentId?: number,
    replyToUserId?: number
  ) => unwrap<CommentItem>(http.post("/api/comments", {
    targetType,
    targetId,
    content,
    parentId,
    replyToUserId
  })),
  remove: (id: number) => unwrap<void>(http.delete(`/api/comments/${id}`)),
  like: (id: number) => unwrap<CommentItem>(http.put(`/api/comments/${id}/like`)),
  unlike: (id: number) => unwrap<CommentItem>(http.delete(`/api/comments/${id}/like`)),
  report: (id: number, reason: string, detail?: string) =>
    unwrap<void>(http.post(`/api/comments/${id}/reports`, { reason, detail }))
};

export const searchApi = {
  all: (keyword: string, signal?: AbortSignal) =>
    unwrap<SearchResponse>(http.get("/api/search", { params: { keyword }, signal })),
  suggestions: (keyword: string, limit = 8, signal?: AbortSignal) =>
    unwrap<SearchSuggestion[]>(http.get("/api/search/suggestions", { params: { keyword, limit }, signal })),
  byType: <T extends SearchResultType>(
    type: T,
    keyword: string,
    page = 1,
    size = 20,
    signal?: AbortSignal
  ) => unwrap<PageResult<SearchResultItemMap[T]>>(
    http.get(`/api/search/${type}`, { params: { keyword, page, size }, signal })
  )
};

export const uploadApi = {
  image: (file: File, purpose: "AVATAR" | "PLAYLIST_COVER") => {
    const form = new FormData();
    form.append("file", file);
    return unwrap<UploadFile>(http.post("/api/uploads/images", form, { params: { purpose } }));
  }
};

export const adminApi = {
  dashboard: () => unwrap<Record<string, number>>(http.get("/api/admin/dashboard")),
  users: (params: { page?: number; size?: number; keyword?: string; role?: string; status?: number } = {}) =>
    unwrap<PageResult<AdminUser>>(http.get("/api/admin/users", { params })),
  deleteUser: (id: number) => unwrap<void>(http.delete(`/api/admin/users/${id}`)),
  songs: (params: { page?: number; size?: number; keyword?: string; status?: number } = {}) =>
    unwrap<PageResult<Song>>(http.get("/api/admin/songs", { params })),
  commentReports: (params: { page?: number; size?: number; status?: string } = {}) =>
    unwrap<PageResult<CommentReportItem>>(http.get("/api/admin/comment-reports", { params })),
  moderateComment: (id: number, action: "HIDE" | "RESTORE" | "PIN" | "UNPIN") =>
    unwrap<CommentItem>(http.patch(`/api/admin/comments/${id}`, { action })),
  createSong: (payload: Partial<Song> & { title: string; artistId: number; albumId: number; audioUrl: string }) =>
    unwrap<Song>(http.post("/api/admin/songs", payload)),
  updateSong: (id: number, payload: Partial<Song> & { title: string; artistId: number; albumId: number; audioUrl: string }) =>
    unwrap<Song>(http.put(`/api/admin/songs/${id}`, payload)),
  updateSongStatus: (id: number, status: number) =>
    unwrap<Song>(http.patch(`/api/admin/songs/${id}/status`, { status })),
  deleteSong: (id: number) => unwrap<void>(http.delete(`/api/admin/songs/${id}`)),
  createArtist: (payload: { name: string; bio?: string; avatarUrl?: string }) =>
    unwrap<Artist>(http.post("/api/admin/artists", payload)),
  updateArtist: (id: number, payload: { name: string; bio?: string; avatarUrl?: string }) =>
    unwrap<Artist>(http.put(`/api/admin/artists/${id}`, payload)),
  deleteArtist: (id: number) => unwrap<void>(http.delete(`/api/admin/artists/${id}`)),
  createAlbum: (payload: { title: string; artistId: number; coverUrl?: string; releaseDate?: string }) =>
    unwrap<Album>(http.post("/api/admin/albums", payload)),
  updateAlbum: (id: number, payload: { title: string; artistId: number; coverUrl?: string; releaseDate?: string }) =>
    unwrap<Album>(http.put(`/api/admin/albums/${id}`, payload)),
  deleteAlbum: (id: number) => unwrap<void>(http.delete(`/api/admin/albums/${id}`)),
  importLddcLyrics: (payload: { title: string; artist: string; album?: string; audioUrl: string; durationSeconds?: number }) =>
    unwrap<LddcLyricResult>(http.post("/api/admin/lyrics/lddc", payload, { timeout: LDDC_IMPORT_TIMEOUT_MS })),
  upload: (file: File, fileType: string) => {
    const form = new FormData();
    form.append("file", file);
    form.append("fileType", fileType);
    return unwrap<UploadFile>(http.post("/api/admin/upload", form));
  }
};
