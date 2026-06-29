SET NAMES utf8mb4;

USE music_web;

INSERT INTO `user` (id, username, password_hash, nickname, avatar_url, role, status)
VALUES
  (1, 'admin', '$2a$10$N6VyIIcvlTGLKQFOgRGcmewyKaHS14Fx/eGzpnMqt1KuN934Bn/ry', '系统管理员', NULL, 'ADMIN', 1),
  (2, 'demo', '$2a$10$SqhOBKQjwQM4PwFMXofE7.K2uvA1KpxlntBb8KXAPoC4z5IsjPrke', '演示用户', NULL, 'USER', 1)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  role = VALUES(role),
  status = VALUES(status);

INSERT INTO artist (id, name, bio, avatar_url)
VALUES (1, '周杰伦', '本地课程演示曲目使用的歌手信息。公开部署前需再次确认素材授权范围。', NULL)
ON DUPLICATE KEY UPDATE
  bio = VALUES(bio),
  avatar_url = VALUES(avatar_url);

INSERT INTO album (id, title, artist_id, cover_url, release_date)
VALUES (1, '太阳之子', 1, NULL, NULL)
ON DUPLICATE KEY UPDATE
  artist_id = VALUES(artist_id),
  cover_url = VALUES(cover_url),
  release_date = VALUES(release_date);

INSERT INTO song (id, title, artist_id, album_id, cover_url, audio_url, lyric_url, duration_seconds, language, genre, mood, status)
VALUES
  (1, 'I Do', 1, 1, NULL, '/media/audio/I%20Do%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '浪漫', 1),
  (2, '太阳之子', 1, 1, NULL, '/media/audio/%E5%A4%AA%E9%98%B3%E4%B9%8B%E5%AD%90%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '热烈', 1)
ON DUPLICATE KEY UPDATE
  audio_url = VALUES(audio_url),
  status = VALUES(status);
