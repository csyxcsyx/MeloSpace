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
  bio?: string | null;
  role: "USER" | "ADMIN";
}

export interface AdminUser {
  id: number;
  username: string;
  nickname: string | null;
  avatarUrl: string | null;
  role: "USER" | "ADMIN";
  status: number;
  passwordState: string;
  createdAt: string;
  updatedAt: string;
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
  creatorNickname: string | null;
  creatorAvatarUrl: string | null;
  tags: string[];
  commentCount: number;
  favorited: boolean;
  canManage: boolean;
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

export interface DiscoverComment {
  id: number;
  userId: number;
  userNickname: string;
  userAvatarUrl: string | null;
  targetType: "SONG" | "PLAYLIST";
  targetId: number;
  targetTitle: string;
  targetCoverUrl: string | null;
  content: string;
  likeCount: number;
  replyCount: number;
  createdAt: string;
}

export interface CommunityDiscover {
  popularPlaylists: Playlist[];
  latestPlaylists: Playlist[];
  hotComments: DiscoverComment[];
}

export interface SearchResponse {
  songs: Song[];
  artists: Artist[];
  albums: Album[];
  playlists: Playlist[];
  users: PublicUserProfile[];
  totals: SearchTotals;
}

export type SearchResultType = "songs" | "artists" | "albums" | "playlists" | "users";
export type SearchTab = "all" | SearchResultType;
export type SearchSuggestionType = "SONG" | "ARTIST" | "ALBUM" | "PLAYLIST" | "USER";

export interface SearchTotals {
  songs: number;
  artists: number;
  albums: number;
  playlists: number;
  users: number;
}

export interface SearchSuggestion {
  type: SearchSuggestionType;
  id: number;
  title: string;
  subtitle: string | null;
  imageUrl: string | null;
}

export interface PublicUserProfile {
  id: number;
  nickname: string;
  avatarUrl: string | null;
  bio: string | null;
  publicPlaylistCount: number;
  receivedFavoriteCount: number;
  commentCount: number;
}

export interface SearchResultItemMap {
  songs: Song;
  artists: Artist;
  albums: Album;
  playlists: Playlist;
  users: PublicUserProfile;
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
  parentId: number | null;
  replyToUserId: number | null;
  replyToNickname: string | null;
  userNickname: string;
  userAvatarUrl: string | null;
  likeCount: number;
  replyCount: number;
  liked: boolean;
  pinned: boolean;
  deleted: boolean;
  mine: boolean;
}

export interface CommentReportItem {
  id: number;
  commentId: number;
  reporterUserId: number;
  reporterNickname: string;
  reason: string;
  detail: string | null;
  status: "OPEN" | "RESOLVED" | "DISMISSED";
  commentContent: string | null;
  commentStatus: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface FavoriteItem {
  id: number;
  userId: number;
  targetType: "SONG" | "PLAYLIST";
  targetId: number;
  createdAt: string;
  song?: Song | null;
  playlist?: Playlist | null;
}

export interface PlayHistoryItem {
  id: number;
  userId: number;
  songId: number;
  progressSeconds: number | null;
  sourceType: string | null;
  playedAt: string;
  song?: Song | null;
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

export interface LddcLyricResult {
  lyricUrl: string;
  outputPath: string;
  source: string | null;
  matchedTitle: string | null;
  matchedArtist: string | null;
  matchedAlbum: string | null;
  durationMs: number | null;
  format: string | null;
}
