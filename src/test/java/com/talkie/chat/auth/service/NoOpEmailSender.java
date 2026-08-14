package com.talkie.chat.auth.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class NoOpEmailSender implements EmailSender {

    @Override
    public void sendVerificationCode(String toEmail, String code) {
    }
}