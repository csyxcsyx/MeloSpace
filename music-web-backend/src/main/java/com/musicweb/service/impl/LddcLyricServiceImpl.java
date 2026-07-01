package com.musicweb.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicweb.common.ErrorCode;
import com.musicweb.config.LddcProperties;
import com.musicweb.config.MediaProperties;
import com.musicweb.dto.LddcLyricRequest;
import com.musicweb.exception.BusinessException;
import com.musicweb.service.LddcLyricService;
import com.musicweb.vo.LddcLyricResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LddcLyricServiceImpl implements LddcLyricService {

    private static final String MEDIA_PREFIX = "/media/";

    private final LddcProperties lddcProperties;
    private final MediaProperties mediaProperties;
    private final ObjectMapper objectMapper;

    public LddcLyricServiceImpl(
            LddcProperties lddcProperties,
            MediaProperties mediaProperties,
            ObjectMapper objectMapper
    ) {
        this.lddcProperties = lddcProperties;
        this.mediaProperties = mediaProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LddcLyricResponse importLyrics(LddcLyricRequest request) {
        Path repoRoot = resolveRepoRoot();
        Path scriptPath = resolveConfiguredPath(lddcProperties.scriptPath(), repoRoot, "scripts/import_lddc_lyrics.py");
        Path lyricsDir = resolveConfiguredPath(lddcProperties.lyricsDir(), repoRoot, "src/main/resources/static/media/lyrics");

        if (!Files.isRegularFile(scriptPath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "LDDC 歌词导入脚本不存在", HttpStatus.NOT_FOUND);
        }

        List<String> command = buildCommand(request, scriptPath, lyricsDir);
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(repoRoot.toFile());
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");

        try {
            Process process = processBuilder.start();
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

            boolean finished = process.waitFor(lddcProperties.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "LDDC 歌词匹配超时", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            String stdout = stdoutFuture.join();
            String stderr = stderrFuture.join();
            if (process.exitValue() != 0) {
                String detail = StringUtils.hasText(stderr) ? stderr.trim() : stdout.trim();
                throw new BusinessException(
                        ErrorCode.SYSTEM_ERROR,
                        "LDDC 歌词匹配失败" + (StringUtils.hasText(detail) ? "：" + detail : ""),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            return toResponse(stdout, lyricsDir);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法启动 LDDC 歌词导入脚本", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "LDDC 歌词匹配被中断", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> buildCommand(LddcLyricRequest request, Path scriptPath, Path lyricsDir) {
        List<String> command = new ArrayList<>();
        command.add(lddcProperties.python());
        command.add(scriptPath.toString());
        addArgument(command, "--title", request.title().trim());
        addArgument(command, "--artist", request.artist().trim());
        if (StringUtils.hasText(request.album())) {
            addArgument(command, "--album", request.album().trim());
        }

        Path audioFile = resolveAudioFile(request.audioUrl());
        if (audioFile != null) {
            addArgument(command, "--audio-file", audioFile.toString());
        } else if (request.durationSeconds() != null && request.durationSeconds() > 0) {
            addArgument(command, "--duration-ms", String.valueOf(request.durationSeconds() * 1000));
        }

        if (StringUtils.hasText(lddcProperties.srcPath())) {
            addArgument(command, "--lddc-src", resolveConfiguredPath(lddcProperties.srcPath(), resolveRepoRoot(), "").toString());
        } else {
            addArgument(command, "--lddc-zip", resolveConfiguredPath(lddcProperties.zipPath(), resolveRepoRoot(), "").toString());
        }
        addArgument(command, "--lyrics-dir", lyricsDir.toString());
        addArgument(command, "--format", lddcProperties.format());
        addArgument(command, "--sources", lddcProperties.sources());
        addArgument(command, "--langs", lddcProperties.langs());
        addArgument(command, "--min-score", String.valueOf(lddcProperties.minScore()));
        command.add("--force");
        return command;
    }

    private void addArgument(List<String> command, String name, String value) {
        command.add(name);
        command.add(value);
    }

    private LddcLyricResponse toResponse(String stdout, Path lyricsDir) {
        try {
            JsonNode result = objectMapper.readTree(extractJson(stdout));
            String outputPath = result.path("output").asText();
            Path output = Path.of(outputPath).toAbsolutePath().normalize();
            return new LddcLyricResponse(
                    buildLyricUrl(output, lyricsDir),
                    output.toString(),
                    result.path("source").asText(null),
                    result.path("matched_title").asText(null),
                    result.path("matched_artist").asText(null),
                    result.path("matched_album").asText(null),
                    result.path("duration_ms").isMissingNode() || result.path("duration_ms").isNull()
                            ? null
                            : result.path("duration_ms").asInt(),
                    result.path("format").asText(null)
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "LDDC 歌词匹配结果解析失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String extractJson(String stdout) {
        int start = stdout.indexOf('{');
        int end = stdout.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("No JSON object in LDDC output");
        }
        return stdout.substring(start, end + 1);
    }

    private String buildLyricUrl(Path output, Path lyricsDir) {
        Path normalizedLyricsDir = lyricsDir.toAbsolutePath().normalize();
        Path relative = output.startsWith(normalizedLyricsDir)
                ? normalizedLyricsDir.relativize(output)
                : output.getFileName();
        String baseUrl = mediaProperties.baseUrl().endsWith("/")
                ? mediaProperties.baseUrl().substring(0, mediaProperties.baseUrl().length() - 1)
                : mediaProperties.baseUrl();
        return baseUrl + "/lyrics/" + encodePath(relative.toString().replace("\\", "/"));
    }

    private String encodePath(String path) {
        return String.join(
                "/",
                List.of(path.split("/")).stream()
                        .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                        .toList()
        );
    }

    private Path resolveAudioFile(String audioUrl) {
        if (!StringUtils.hasText(audioUrl)) {
            return null;
        }
        String relativePath = audioUrl.trim();
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return null;
        }

        int mediaIndex = relativePath.indexOf(MEDIA_PREFIX);
        if (mediaIndex >= 0) {
            relativePath = relativePath.substring(mediaIndex + MEDIA_PREFIX.length());
        } else if (relativePath.startsWith("media/")) {
            relativePath = relativePath.substring("media/".length());
        } else if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        relativePath = URLDecoder.decode(relativePath, StandardCharsets.UTF_8);

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path repoRoot = resolveRepoRoot();
        List<Path> candidates = List.of(
                resolveStorageRoot().resolve(relativePath),
                repoRoot.resolve("src/main/resources/static/media").resolve(relativePath),
                cwd.resolve("src/main/resources/static/media").resolve(relativePath),
                cwd.resolve("../src/main/resources/static/media").normalize().resolve(relativePath)
        );
        return candidates.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
    }

    private Path resolveStorageRoot() {
        Path storageRoot = Path.of(mediaProperties.storageRoot());
        if (!storageRoot.isAbsolute()) {
            storageRoot = Path.of("").toAbsolutePath().normalize().resolve(storageRoot);
        }
        return storageRoot.toAbsolutePath().normalize();
    }

    private Path resolveConfiguredPath(String configuredPath, Path repoRoot, String fallbackUnderRepo) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd.resolve(path));
        candidates.add(repoRoot.resolve(path));
        if (StringUtils.hasText(fallbackUnderRepo)) {
            candidates.add(repoRoot.resolve(fallbackUnderRepo));
        }
        return candidates.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(candidate -> Files.exists(candidate) || !StringUtils.hasText(fallbackUnderRepo))
                .findFirst()
                .orElseGet(() -> cwd.resolve(path).toAbsolutePath().normalize());
    }

    private Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("music-web-backend")) && Files.isDirectory(cwd.resolve("scripts"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("music-web-backend")) && Files.isDirectory(parent.resolve("scripts"))) {
            return parent;
        }
        return cwd;
    }

    private String readStream(InputStream inputStream) {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }
}
