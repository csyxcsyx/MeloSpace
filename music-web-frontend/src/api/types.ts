export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface UserSummary {
  id: number;
  username: string;
  nickname: string | null;
  avatarUrl: string | null;
  role: "USER" | "ADMIN";
}

export interface AuthResponse {
  token: string;
  user: UserSummary;
}

export interface Artist {
  id: number;
  name: string;
  bio: string | null;
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Album {
  id: number;
  title: string;
  artistId: number;
  artistName: string | null;
  coverUrl: string | null;
  releaseDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Song {
  id: number;
  title: string;
  artistId: number;
  artistName: string | null;
  albumId: number | null;
  albumTitle: string | null;
  coverUrl: string | null;
  audioUrl: string;
  lyricUrl: string | null;
  durationSeconds: number | null;
  language: string | null;
  genre: string | null;
  mood: string | null;
  playCount: number;
  status: number;
  createdAt: string;
  updatedAt: string;
}

export interface Playlist {
  id: number;
  userId: number;
  title: string;
  description: string | null;
  coverUrl: string | null;
  visibility: "PUBLIC" | "PRIVATE";
  playCount: number;
  favoriteCount: number;
  songCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PlaylistSong {
  id: number;
  songId: number;
  sortOrder: number;
  song: Song;
  createdAt: string;
}

export interface PlaylistDetail extends Playlist {
  songs: PlaylistSong[];
}

export interface SearchResponse {
  songs: Song[];
  artists: Artist[];
  albums: Album[];
  playlists: Playlist[];
}

export interface CommentItem {
  id: number;
  userId: number;
  targetType: "SONG" | "PLAYLIST";
  targetId: number;
  content: string;
  status: number;
  createdAt: string;
  updatedAt: string;
}

export interface FavoriteItem {
  id: number;
  userId: number;
  targetType: "SONG" | "PLAYLIST";
  targetId: number;
  createdAt: string;
}

export interface PlayHistoryItem {
  id: number;
  userId: number;
  songId: number;
  progressSeconds: number | null;
  sourceType: string | null;
  playedAt: string;
}

export interface UploadFile {
  id: number;
  fileType: string;
  originalName: string;
  url: string;
  mimeType: string;
  sizeBytes: number;
  createdAt: string;
}
