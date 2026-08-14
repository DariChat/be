package com.talkie.chat.auth.service;

public interface EmailSender {
    void sendVerificationCode(String toEmail, String code);
}