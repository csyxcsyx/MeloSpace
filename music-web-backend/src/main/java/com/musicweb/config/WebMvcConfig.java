package com.musicweb.config;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final MediaProperties mediaProperties;

    public WebMvcConfig(MediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**").addResourceLocations(mediaLocations());
    }

    private String[] mediaLocations() {
        Set<String> locations = new LinkedHashSet<>();
        locations.add(toResourceLocation(Path.of(mediaProperties.storageRoot())));
        locations.add(toResourceLocation(Path.of("src/main/resources/static/media")));
        locations.add(toResourceLocation(Path.of("../src/main/resources/static/media")));
        locations.add("classpath:/static/media/");
        return locations.toArray(String[]::new);
    }

    private String toResourceLocation(Path path) {
        String location = path.toAbsolutePath().normalize().toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
