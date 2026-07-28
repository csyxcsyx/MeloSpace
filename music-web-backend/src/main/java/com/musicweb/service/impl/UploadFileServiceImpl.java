package com.musicweb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.musicweb.common.ErrorCode;
import com.musicweb.config.MediaProperties;
import com.musicweb.entity.UploadFile;
import com.musicweb.exception.BusinessException;
import com.musicweb.mapper.UploadFileMapper;
import com.musicweb.service.UploadFileService;
import com.musicweb.support.UploadFileResponseAssembler;
import com.musicweb.vo.UploadFileResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadFileServiceImpl extends ServiceImpl<UploadFileMapper, UploadFile> implements UploadFileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadFileServiceImpl.class);

    private final MediaProperties mediaProperties;

    public UploadFileServiceImpl(MediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    @Override
    public UploadFileResponse upload(MultipartFile file, String fileType, Long ownerId) {
        return upload(file, getUploadSpec(fileType), ownerId, false);
    }

    @Override
    public UploadFileResponse uploadImage(MultipartFile file, String purpose, Long ownerId) {
        return upload(file, getImageUploadSpec(purpose), ownerId, true);
    }

    private UploadFileResponse upload(
            MultipartFile file,
            UploadSpec uploadSpec,
            Long ownerId,
            boolean verifyImageContent
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上传文件不能为空", HttpStatus.BAD_REQUEST);
        }
        validateSize(file, uploadSpec);

        String originalName = StringUtils.getFilename(file.getOriginalFilename());
        if (!StringUtils.hasText(originalName)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空", HttpStatus.BAD_REQUEST);
        }
        String extension = StringUtils.getFilenameExtension(originalName);
        if (!StringUtils.hasText(extension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件扩展名不能为空", HttpStatus.BAD_REQUEST);
        }
        extension = extension.toLowerCase(Locale.ROOT);
        validateExtension(extension, uploadSpec);
        validateMimeType(file.getContentType(), uploadSpec);
        if (verifyImageContent) {
            validateImageContent(file, extension);
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path root = Path.of(mediaProperties.storageRoot()).toAbsolutePath().normalize();
        Path folder = root.resolve(uploadSpec.folder()).normalize();
        Path target = folder.resolve(storedName).normalize();
        if (!folder.startsWith(root) || !target.startsWith(folder)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件路径非法", HttpStatus.BAD_REQUEST);
        }

        try {
            Files.createDirectories(folder);
            if (!Files.isDirectory(folder) || !Files.isWritable(folder)) {
                throw new IOException("Upload folder is not writable: " + folder);
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error(
                    "Failed to save {} upload '{}' to {}",
                    uploadSpec.fileType(),
                    originalName,
                    target,
                    exception
            );
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败，请检查媒体目录写入权限", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String relativePath = uploadSpec.folder() + "/" + storedName;
        UploadFile uploadFile = new UploadFile();
        uploadFile.setOwnerId(ownerId);
        uploadFile.setFileType(uploadSpec.fileType());
        uploadFile.setOriginalName(originalName);
        uploadFile.setStoragePath(relativePath);
        uploadFile.setUrl(buildUrl(relativePath));
        uploadFile.setMimeType(StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream");
        uploadFile.setSizeBytes(file.getSize());
        save(uploadFile);
        return UploadFileResponseAssembler.toUploadFileResponse(getById(uploadFile.getId()));
    }

    private UploadSpec getImageUploadSpec(String purpose) {
        if (!StringUtils.hasText(purpose)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片用途不能为空", HttpStatus.BAD_REQUEST);
        }
        String normalized = purpose.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AVATAR" -> new UploadSpec(
                    "AVATAR",
                    "avatar",
                    Set.of("jpg", "jpeg", "png", "webp"),
                    DataSize.ofMegabytes(5),
                    Set.of("image/jpeg", "image/png", "image/webp")
            );
            case "PLAYLIST_COVER" -> new UploadSpec(
                    "PLAYLIST_COVER",
                    "playlist-cover",
                    Set.of("jpg", "jpeg", "png", "webp"),
                    DataSize.ofMegabytes(5),
                    Set.of("image/jpeg", "image/png", "image/webp")
            );
            default -> throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "图片用途仅支持 AVATAR 或 PLAYLIST_COVER",
                    HttpStatus.BAD_REQUEST
            );
        };
    }

    private UploadSpec getUploadSpec(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "fileType 不能为空", HttpStatus.BAD_REQUEST);
        }
        String normalized = fileType.trim().toUpperCase(Locale.ROOT);
        Map<String, UploadSpec> specs = Map.of(
                "AUDIO", new UploadSpec(
                        "AUDIO",
                        "audio",
                        Set.of("mp3", "aac", "m4a", "flac", "wav"),
                        mediaProperties.maxAudioSize(),
                        Set.of("audio/", "application/octet-stream")
                ),
                "COVER", new UploadSpec(
                        "COVER",
                        "cover",
                        Set.of("jpg", "jpeg", "png", "webp"),
                        mediaProperties.maxCoverSize(),
                        Set.of("image/", "application/octet-stream")
                ),
                "ARTIST", new UploadSpec(
                        "ARTIST",
                        "artist",
                        Set.of("jpg", "jpeg", "png", "webp"),
                        mediaProperties.maxCoverSize(),
                        Set.of("image/", "application/octet-stream")
                ),
                "LYRIC", new UploadSpec(
                        "LYRIC",
                        "lyrics",
                        Set.of("lrc", "txt"),
                        mediaProperties.maxLyricSize(),
                        Set.of("text/", "application/octet-stream")
                )
        );
        UploadSpec uploadSpec = specs.get(normalized);
        if (uploadSpec == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的文件类型", HttpStatus.BAD_REQUEST);
        }
        return uploadSpec;
    }

    private void validateSize(MultipartFile file, UploadSpec uploadSpec) {
        if (file.getSize() > uploadSpec.maxSize().toBytes()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件大小超出限制", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateExtension(String extension, UploadSpec uploadSpec) {
        if (!uploadSpec.extensions().contains(extension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的文件扩展名", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateMimeType(String contentType, UploadSpec uploadSpec) {
        if (!StringUtils.hasText(contentType)) {
            return;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        boolean supported = uploadSpec.mimePrefixes().stream().anyMatch(normalized::startsWith);
        if (!supported) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的 MIME 类型", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateImageContent(MultipartFile file, String extension) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(12);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无法读取图片内容", HttpStatus.BAD_REQUEST);
        }

        String detectedType = detectImageType(header, header.length);
        String expectedType = switch (extension) {
            case "jpg", "jpeg" -> "JPEG";
            case "png" -> "PNG";
            case "webp" -> "WEBP";
            default -> "";
        };
        String contentType = file.getContentType();
        String normalizedMime = StringUtils.hasText(contentType)
                ? contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim()
                : "";
        String expectedMime = switch (expectedType) {
            case "JPEG" -> "image/jpeg";
            case "PNG" -> "image/png";
            case "WEBP" -> "image/webp";
            default -> "";
        };
        if (!expectedType.equals(detectedType) || !expectedMime.equals(normalizedMime)) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "图片内容、扩展名与 MIME 类型必须一致",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String detectImageType(byte[] header, int bytesRead) {
        if (bytesRead >= 3
                && unsigned(header[0]) == 0xff
                && unsigned(header[1]) == 0xd8
                && unsigned(header[2]) == 0xff) {
            return "JPEG";
        }
        if (bytesRead >= 8
                && unsigned(header[0]) == 0x89
                && header[1] == 'P'
                && header[2] == 'N'
                && header[3] == 'G'
                && unsigned(header[4]) == 0x0d
                && unsigned(header[5]) == 0x0a
                && unsigned(header[6]) == 0x1a
                && unsigned(header[7]) == 0x0a) {
            return "PNG";
        }
        if (bytesRead >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {
            return "WEBP";
        }
        return "UNKNOWN";
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private String buildUrl(String relativePath) {
        String baseUrl = mediaProperties.baseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBase + "/" + relativePath.replace("\\", "/");
    }

    private record UploadSpec(
            String fileType,
            String folder,
            Set<String> extensions,
            DataSize maxSize,
            Set<String> mimePrefixes
    ) {
    }
}
