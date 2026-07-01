CREATE DATABASE IF NOT EXISTS music_web
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE music_web;

CREATE TABLE IF NOT EXISTS `user` (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(50) NOT NULL,
  avatar_url VARCHAR(500) NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_role (role),
  KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `user`
  MODIFY username VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL;

CREATE TABLE IF NOT EXISTS artist (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  bio VARCHAR(1000) NULL,
  avatar_url VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_artist_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS album (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  artist_id BIGINT NOT NULL,
  cover_url VARCHAR(500) NULL,
  release_date DATE NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_album_artist_id (artist_id),
  CONSTRAINT fk_album_artist FOREIGN KEY (artist_id) REFERENCES artist (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS song (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  artist_id BIGINT NOT NULL,
  album_id BIGINT NULL,
  cover_url VARCHAR(500) NULL,
  audio_url VARCHAR(500) NOT NULL,
  lyric_url VARCHAR(500) NULL,
  duration_seconds INT NOT NULL DEFAULT 0,
  language VARCHAR(20) NULL,
  genre VARCHAR(50) NULL,
  mood VARCHAR(50) NULL,
  play_count BIGINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_song_title (title),
  KEY idx_song_artist_id (artist_id),
  KEY idx_song_album_id (album_id),
  KEY idx_song_status (status),
  CONSTRAINT fk_song_artist FOREIGN KEY (artist_id) REFERENCES artist (id),
  CONSTRAINT fk_song_album FOREIGN KEY (album_id) REFERENCES album (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS playlist (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  cover_url VARCHAR(500) NULL,
  visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
  play_count BIGINT NOT NULL DEFAULT 0,
  favorite_count BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_playlist_user_id (user_id),
  KEY idx_playlist_visibility (visibility),
  KEY idx_playlist_play_count (play_count),
  CONSTRAINT fk_playlist_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS playlist_song (
  id BIGINT NOT NULL AUTO_INCREMENT,
  playlist_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_playlist_song (playlist_id, song_id),
  KEY idx_playlist_song_order (playlist_id, sort_order),
  KEY idx_playlist_song_song_id (song_id),
  CONSTRAINT fk_playlist_song_playlist FOREIGN KEY (playlist_id) REFERENCES playlist (id) ON DELETE CASCADE,
  CONSTRAINT fk_playlist_song_song FOREIGN KEY (song_id) REFERENCES song (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS favorite (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_target (user_id, target_type, target_id),
  KEY idx_favorite_user_id (user_id),
  CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS comment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_comment_target (target_type, target_id, created_at),
  KEY idx_comment_user_id (user_id),
  CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS play_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  song_id BIGINT NOT NULL,
  progress_seconds INT NOT NULL DEFAULT 0,
  source_type VARCHAR(30) NULL,
  played_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_play_history_user_played_at (user_id, played_at),
  KEY idx_play_history_song_id (song_id),
  CONSTRAINT fk_play_history_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
  CONSTRAINT fk_play_history_song FOREIGN KEY (song_id) REFERENCES song (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS upload_file (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  file_type VARCHAR(20) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  url VARCHAR(500) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_upload_file_owner_id (owner_id),
  KEY idx_upload_file_type (file_type),
  CONSTRAINT fk_upload_file_owner FOREIGN KEY (owner_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
