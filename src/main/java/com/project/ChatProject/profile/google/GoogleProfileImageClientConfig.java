package com.project.ChatProject.profile.google;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class GoogleProfileImageClientConfig {

    @Bean
    public RestClient googleProfileImageRestClient(
            RestClient.Builder builder,
            GoogleProfileImageDownloadProperties properties
    ) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(
                        HttpClient.Redirect.NEVER
                )
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                properties.readTimeout()
        );

        return builder
                .requestFactory(requestFactory)
                .build();
    }

}
