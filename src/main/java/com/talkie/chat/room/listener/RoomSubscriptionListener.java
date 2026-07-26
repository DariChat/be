package com.talkie.chat.room.listener;

import com.talkie.chat.global.redis.MessageBroadcastedEvent;
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
import java.util.Set;
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

    // key: roomId, value: 그 방을 구독 중인 userId 목록 (다중 세션 대비 참조 카운트)
    private final Map<Long, Map<Long, Integer>> roomSubscriberCounts = new ConcurrentHashMap<>();

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

        Principal user = accessor.getUser();
        if (user != null) {
            addSubscriber(roomId, Long.valueOf(user.getName()));
        }

        markAsRead(user, roomId);
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

        Principal user = accessor.getUser();
        if (user != null) {
            removeSubscriber(roomId, Long.valueOf(user.getName()));
        }

        markAsRead(user, roomId);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(event.getMessage(), StompHeaderAccessor.class);
        if (accessor == null) {
            return;
        }

        Map<String, Long> subscriptions = sessionRoomSubscriptions.remove(accessor.getSessionId());
        if (subscriptions == null) {
            return;
        }

        Principal user = accessor.getUser();
        if (user == null) {
            return;
        }
        Long userId = Long.valueOf(user.getName());
        subscriptions.values().forEach(roomId -> removeSubscriber(roomId, userId));
    }

    /**
     * 같은 인스턴스에 연결된 구독자만 대상으로 읽음 처리한다.
     * Redis pub/sub로 메시지를 수신한 모든 인스턴스에서 각자 호출되므로,
     * 인스턴스 전체로 보면 전체 구독자에 대한 읽음 처리가 이루어진다.
     */
    @EventListener
    public void handleMessageBroadcasted(MessageBroadcastedEvent event) {
        Set<Long> subscriberIds = getSubscriberIds(event.roomId());
        roomService.markAsRead(subscriberIds, event.roomId(), event.messageId());
    }

    /**
     * 현재 해당 방을 구독 중인 유저 id 목록 (실시간 읽음 처리에 사용)
     */
    public Set<Long> getSubscriberIds(Long roomId) {
        Map<Long, Integer> subscribers = roomSubscriberCounts.get(roomId);
        return subscribers == null ? Set.of() : Set.copyOf(subscribers.keySet());
    }

    private void addSubscriber(Long roomId, Long userId) {
        roomSubscriberCounts
                .computeIfAbsent(roomId, key -> new ConcurrentHashMap<>())
                .merge(userId, 1, Integer::sum);
    }

    private void removeSubscriber(Long roomId, Long userId) {
        roomSubscriberCounts.computeIfPresent(roomId, (key, subscribers) -> decrementAndPruneIfEmpty(subscribers, userId));
    }

    private Map<Long, Integer> decrementAndPruneIfEmpty(Map<Long, Integer> subscribers, Long userId) {
        subscribers.computeIfPresent(userId, (uid, count) -> count > 1 ? count - 1 : null);
        return subscribers.isEmpty() ? null : subscribers;
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