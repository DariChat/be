package com.talkie.chat.friend.listener;

import com.talkie.chat.friend.dto.FriendEvent;
import com.talkie.chat.friend.event.FriendRequestAcceptedEvent;
import com.talkie.chat.friend.event.FriendRequestReceivedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FriendNotificationListener {

    private static final String DESTINATION = "/queue/friends";

    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestReceived(FriendRequestReceivedEvent event) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(event.addresseeId()),
                DESTINATION,
                FriendEvent.requestReceived(event.request())
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestAccepted(FriendRequestAcceptedEvent event) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(event.requesterId()),
                DESTINATION,
                FriendEvent.requestAccepted(event.friend())
        );
    }
}