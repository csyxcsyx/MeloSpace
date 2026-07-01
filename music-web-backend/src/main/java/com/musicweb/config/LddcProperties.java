package com.musicweb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "music-web.lddc")
public record LddcProperties(
        String python,
        String scriptPath,
        String zipPath,
        String srcPath,
        String lyricsDir,
        String format,
        String sources,
        String langs,
        double minScore,
        long timeoutSeconds
) {
    public LddcProperties {
        python = python == null || python.isBlank() ? "python" : python;
        scriptPath = scriptPath == null || scriptPath.isBlank() ? "../scripts/import_lddc_lyrics.py" : scriptPath;
        zipPath = zipPath == null || zipPath.isBlank()
                ? "C:\\Users\\YUXIANde\\Downloads\\LDDC-0.9.2.zip"
                : zipPath;
        srcPath = srcPath == null ? "" : srcPath;
        lyricsDir = lyricsDir == null || lyricsDir.isBlank()
                ? "../src/main/resources/static/media/lyrics"
                : lyricsDir;
        format = format == null || format.isBlank() ? "enhanced-lrc" : format;
        sources = sources == null || sources.isBlank() ? "QM,KG,NE" : sources;
        langs = langs == null || langs.isBlank() ? "orig" : langs;
        minScore = minScore <= 0 ? 55.0 : minScore;
        timeoutSeconds = timeoutSeconds <= 0 ? 180 : timeoutSeconds;
    }
}
