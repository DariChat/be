package com.talkie.chat.auth.service;

import com.talkie.chat.auth.exception.AuthErrorCode;
import com.talkie.chat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[Talkie] 이메일 인증코드");
        message.setText("인증코드는 " + code + " 입니다. (5분간 유효)");

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new BusinessException(AuthErrorCode.EMAIL_SEND_FAILED, e);
        }
    }
}