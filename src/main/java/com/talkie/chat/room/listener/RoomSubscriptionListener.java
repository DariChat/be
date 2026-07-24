package com.talkie.chat.room.listener;

import com.talkie.chat.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RoomSubscriptionListener {

    private static final Pattern ROOM_DESTINATION_PATTERN = Pattern.compile("^/sub/rooms/(\\d+)$");

    private final RoomService roomService;

    // key: sessionId, value: (subscriptionId -> roomId)
    private final Map<String, Map<String, Long>> sessionRoomSubscriptions = new ConcurrentHashMap<>();

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);
        if (accessor == null) {
            return;
        }

        Long roomId = extractRoomId(accessor.getDestination());
        if (roomId == null) {
            return;
        }

        sessionRoomSubscriptions
                .computeIfAbsent(accessor.getSessionId(), key -> new ConcurrentHashMap<>())
                .put(accessor.getSubscriptionId(), roomId);

        markAsRead(accessor.getUser(), roomId);
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);
        if (accessor == null) {
            return;
        }

        Map<String, Long> subscriptions = sessionRoomSubscriptions.get(accessor.getSessionId());
        if (subscriptions == null) {
            return;
        }

        Long roomId = subscriptions.remove(accessor.getSubscriptionId());
        if (roomId == null) {
            return;
        }

        markAsRead(accessor.getUser(), roomId);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);
        if (accessor == null) {
            return;
        }
        sessionRoomSubscriptions.remove(accessor.getSessionId());
    }

    private Long extractRoomId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = ROOM_DESTINATION_PATTERN.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }

    private void markAsRead(Principal user, Long roomId) {
        if (user == null) {
            return;
        }
        Long userId = Long.valueOf(user.getName());
        roomService.markAsRead(userId, roomId);
    }
}