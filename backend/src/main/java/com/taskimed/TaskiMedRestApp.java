package com.taskimed;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication(scanBasePackages = "com.taskimed")
public class TaskiMedRestApp {

	@Value("${ml.base-url}")
	private String mlBaseUrl;

	public static void main(String[] args) {
		SpringApplication.run(TaskiMedRestApp.class, args);
	}

    @Bean
    public Hibernate6Module hibernate5Module() {
        return new Hibernate6Module();
    }

    @Bean
    public WebClient mlWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl(mlBaseUrl)
            .build();
    }
}