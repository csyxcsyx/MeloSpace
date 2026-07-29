CREATE TABLE playlist_tag (
  id BIGINT NOT NULL AUTO_INCREMENT,
  playlist_id BIGINT NOT NULL,
  tag VARCHAR(12) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_playlist_tag (playlist_id, tag),
  KEY idx_playlist_tag_lookup (tag, playlist_id),
  CONSTRAINT fk_playlist_tag_playlist FOREIGN KEY (playlist_id) REFERENCES playlist (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_playlist_visibility_popularity
  ON playlist (visibility, favorite_count, play_count, updated_at);

CREATE INDEX idx_favorite_target_lookup
  ON favorite (target_type, target_id, user_id);
