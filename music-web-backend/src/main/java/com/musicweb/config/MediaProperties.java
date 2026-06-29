package com.musicweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "music-web.media")
public record MediaProperties(
        String baseUrl,
        String storageRoot,
        DataSize maxAudioSize,
        DataSize maxCoverSize,
        DataSize maxLyricSize
) {
    public MediaProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "/media" : baseUrl;
        storageRoot = storageRoot == null || storageRoot.isBlank() ? "./media" : storageRoot;
        maxAudioSize = maxAudioSize == null ? DataSize.ofMegabytes(100) : maxAudioSize;
        maxCoverSize = maxCoverSize == null ? DataSize.ofMegabytes(5) : maxCoverSize;
        maxLyricSize = maxLyricSize == null ? DataSize.ofMegabytes(1) : maxLyricSize;
    }
}
