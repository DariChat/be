package com.talkie.chat.global.filter;

import com.talkie.chat.auth.exception.AuthErrorCode;
import com.talkie.chat.global.exception.BusinessException;
import com.talkie.chat.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private final JwtProvider jwtProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            jwtProvider.extractValidateUserId(token, "access")
                    .ifPresentOrElse(userId -> accessor.setUser(() -> String.valueOf(userId)),
                            () -> { throw new BusinessException(AuthErrorCode.INVALID_TOKEN); }
                    );
        }
        return message;
    }
}
