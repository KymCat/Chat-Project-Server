package com.project.ChatProject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class ChatProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatProjectApplication.class, args);
	}

}
