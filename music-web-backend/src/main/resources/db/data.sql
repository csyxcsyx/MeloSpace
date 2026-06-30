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
VALUES
  (1, '周杰伦', '本地课程演示曲目使用的歌手信息。公开部署前需再次确认素材授权范围。', NULL),
  (2, '周杰伦 feat. 杨瑞代', '本地课程演示曲目使用的合作歌手信息。公开部署前需再次确认素材授权范围。', NULL)
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
  (2, '太阳之子', 1, 1, NULL, '/media/audio/%E5%A4%AA%E9%98%B3%E4%B9%8B%E5%AD%90%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '热烈', 1),
  (3, '爱琴海', 1, 1, NULL, '/media/audio/%E7%88%B1%E7%90%B4%E6%B5%B7%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '浪漫', 1),
  (4, '那天下雨了', 1, 1, NULL, '/media/audio/%E9%82%A3%E5%A4%A9%E4%B8%8B%E9%9B%A8%E4%BA%86%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '怀旧', 1),
  (5, '女儿殿下', 1, 1, NULL, '/media/audio/%E5%A5%B3%E5%84%BF%E6%AE%BF%E4%B8%8B%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '温柔', 1),
  (6, '七月的极光', 1, 1, NULL, '/media/audio/%E4%B8%83%E6%9C%88%E7%9A%84%E6%9E%81%E5%85%89%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '梦幻', 1),
  (7, '谁稀罕', 1, 1, NULL, '/media/audio/%E8%B0%81%E7%A8%80%E7%BD%95%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '洒脱', 1),
  (8, '圣诞星', 2, 1, NULL, '/media/audio/%E5%9C%A3%E8%AF%9E%E6%98%9F%20(feat.%20%E6%9D%A8%E7%91%9E%E4%BB%A3)%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '节日', 1),
  (9, '圣徒', 1, 1, NULL, '/media/audio/%E5%9C%A3%E5%BE%92%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '庄重', 1),
  (10, '淘金小镇', 1, 1, NULL, '/media/audio/%E6%B7%98%E9%87%91%E5%B0%8F%E9%95%87%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '叙事', 1),
  (11, '西西里', 1, 1, NULL, '/media/audio/%E8%A5%BF%E8%A5%BF%E9%87%8C%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '异域', 1),
  (12, '乡间的路', 1, 1, NULL, '/media/audio/%E4%B9%A1%E9%97%B4%E7%9A%84%E8%B7%AF%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '治愈', 1),
  (13, '湘女多情', 1, 1, NULL, '/media/audio/%E6%B9%98%E5%A5%B3%E5%A4%9A%E6%83%85%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac', NULL, 0, '中文', 'Pop', '柔情', 1)
ON DUPLICATE KEY UPDATE
  audio_url = VALUES(audio_url),
  artist_id = VALUES(artist_id),
  album_id = VALUES(album_id),
  language = VALUES(language),
  genre = VALUES(genre),
  mood = VALUES(mood),
  status = VALUES(status);
