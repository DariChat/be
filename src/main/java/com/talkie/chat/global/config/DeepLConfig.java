package com.talkie.chat.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class DeepLConfig {

    @Value("${deepl.api-key}")
    private String apiKey;

    @Value("${deepl.base-url}")
    private String baseUrl;

    @Bean
    public RestClient deepLRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "DeepL-Auth-Key " + apiKey)
                .build();
    }
}