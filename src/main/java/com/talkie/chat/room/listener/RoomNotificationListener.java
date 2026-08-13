package com.talkie.chat.room.listener;

import com.talkie.chat.room.dto.RoomEvent;
import com.talkie.chat.room.dto.RoomSummaryResponse;
import com.talkie.chat.room.event.RoomCreatedEvent;
import com.talkie.chat.room.event.RoomUpdatedEvent;
import com.talkie.chat.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RoomNotificationListener {

    private static final String DESTINATION = "/queue/rooms";

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomCreated(RoomCreatedEvent event) {
        for (Long memberId : event.memberIds()) {
            RoomSummaryResponse summary = roomService.getRoomSummaryFor(memberId, event.roomId());
            messagingTemplate.convertAndSendToUser(String.valueOf(memberId), DESTINATION, RoomEvent.created(summary));
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomUpdated(RoomUpdatedEvent event) {
        for (Long memberId : event.memberIds()) {
            RoomSummaryResponse summary = roomService.getRoomSummaryFor(memberId, event.roomId());
            messagingTemplate.convertAndSendToUser(String.valueOf(memberId), DESTINATION, RoomEvent.updated(summary));
        }
    }
}