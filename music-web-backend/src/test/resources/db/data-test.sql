INSERT INTO `user` (id, username, password_hash, nickname, avatar_url, role, status)
VALUES
  (1, 'admin', '$2a$10$N6VyIIcvlTGLKQFOgRGcmewyKaHS14Fx/eGzpnMqt1KuN934Bn/ry', '系统管理员', NULL, 'ADMIN', 1),
  (2, 'demo', '$2a$10$SqhOBKQjwQM4PwFMXofE7.K2uvA1KpxlntBb8KXAPoC4z5IsjPrke', '演示用户', NULL, 'USER', 1);

INSERT INTO artist (id, name, bio, avatar_url)
VALUES (1, '周杰伦', '测试歌手', NULL);

INSERT INTO album (id, title, artist_id, cover_url, release_date)
VALUES (1, '太阳之子', 1, NULL, NULL);

INSERT INTO song (id, title, artist_id, album_id, cover_url, audio_url, lyric_url, duration_seconds, language, genre, mood, status)
VALUES
  (1, 'I Do', 1, 1, NULL, '/media/audio/I%20Do%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '浪漫', 1),
  (2, '下架测试歌', 1, 1, NULL, '/media/audio/offline.flac', NULL, 0, '中文', 'Pop', '测试', 0);
