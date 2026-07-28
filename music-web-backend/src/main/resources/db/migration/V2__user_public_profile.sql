ALTER TABLE `user`
  ADD COLUMN bio VARCHAR(500) NULL;

CREATE INDEX idx_user_status_nickname
  ON `user` (status, nickname);

CREATE INDEX idx_playlist_user_visibility_updated_at
  ON playlist (user_id, visibility, updated_at);
