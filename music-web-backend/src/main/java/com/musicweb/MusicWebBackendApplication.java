package com.musicweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MusicWebBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MusicWebBackendApplication.class, args);
	}

}
