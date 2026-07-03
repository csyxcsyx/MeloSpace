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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadFileServiceImpl extends ServiceImpl<UploadFileMapper, UploadFile> implements UploadFileService {

    private final MediaProperties mediaProperties;

    public UploadFileServiceImpl(MediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    @Override
    public UploadFileResponse upload(MultipartFile file, String fileType, Long ownerId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上传文件不能为空", HttpStatus.BAD_REQUEST);
        }
        UploadSpec uploadSpec = getUploadSpec(fileType);
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

        String storedName = UUID.randomUUID() + "." + extension;
        Path root = Path.of(mediaProperties.storageRoot()).toAbsolutePath().normalize();
        Path folder = root.resolve(uploadSpec.folder()).normalize();
        Path target = folder.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件路径非法", HttpStatus.BAD_REQUEST);
        }

        try {
            Files.createDirectories(folder);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
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
        String normalized = contentType.toLowerCase(Locale.ROOT);
        boolean supported = uploadSpec.mimePrefixes().stream().anyMatch(normalized::startsWith);
        if (!supported) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的 MIME 类型", HttpStatus.BAD_REQUEST);
        }
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
