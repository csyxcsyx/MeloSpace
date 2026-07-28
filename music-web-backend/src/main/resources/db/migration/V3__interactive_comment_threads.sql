ALTER TABLE comment
  ADD COLUMN parent_id BIGINT NULL,
  ADD COLUMN reply_to_user_id BIGINT NULL,
  ADD COLUMN like_count INT NOT NULL DEFAULT 0,
  ADD COLUMN reply_count INT NOT NULL DEFAULT 0,
  ADD COLUMN is_pinned TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN deleted_at DATETIME NULL;

CREATE INDEX idx_comment_thread
  ON comment (target_type, target_id, parent_id, status, is_pinned, created_at);

CREATE INDEX idx_comment_parent_created_at
  ON comment (parent_id, status, created_at);

CREATE TABLE comment_like (
  id BIGINT NOT NULL AUTO_INCREMENT,
  comment_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_comment_like (comment_id, user_id),
  KEY idx_comment_like_user_id (user_id),
  CONSTRAINT fk_comment_like_comment FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE CASCADE,
  CONSTRAINT fk_comment_like_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comment_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  comment_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  reason VARCHAR(30) NOT NULL,
  detail VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_comment_report (comment_id, user_id),
  KEY idx_comment_report_status_created_at (status, created_at),
  KEY idx_comment_report_user_created_at (user_id, created_at),
  CONSTRAINT fk_comment_report_comment FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE CASCADE,
  CONSTRAINT fk_comment_report_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
