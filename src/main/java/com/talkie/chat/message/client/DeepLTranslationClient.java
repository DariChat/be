package com.talkie.chat.message.client;

import com.talkie.chat.message.exception.TranslationException;
import com.talkie.chat.user.enums.PreferredLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DeepLTranslationClient {

    private final RestClient deepLRestClient;

    public String translate(String text, PreferredLanguage targetLanguage) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("text", text);
        body.add("target_lang", targetLanguage.name());

        try {
            DeepLResponse response = deepLRestClient.post()
                    .uri("/v2/translate")
                    .body(body)
                    .retrieve()
                    .body(DeepLResponse.class);

            return response.translations().get(0).text();
        } catch (Exception e) {
            throw new TranslationException("DeepL 번역 요청에 실패했습니다. targetLanguage=" + targetLanguage, e);
        }
    }

    private record DeepLResponse(List<Translation> translations) {
    }

    private record Translation(String text) {
    }
}