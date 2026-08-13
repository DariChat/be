package com.talkie.chat.message.client;

import com.talkie.chat.message.exception.TranslationException;
import com.talkie.chat.user.enums.PreferredLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetryableTranslationClient {

    private final DeepLTranslationClient deepLTranslationClient;

    @Retryable(retryFor = TranslationException.class, maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 2))
    public String translate(String content, PreferredLanguage targetLanguage) {
        return deepLTranslationClient.translate(content, targetLanguage);
    }
}