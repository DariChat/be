package com.talkie.chat.room.controller;

import com.talkie.chat.room.dto.RoomCreateRequest;
import com.talkie.chat.room.dto.RoomResponse;
import com.talkie.chat.room.dto.RoomSummaryResponse;
import com.talkie.chat.room.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@AuthenticationPrincipal Long userId, @Valid @RequestBody RoomCreateRequest request) {
        RoomResponse roomResponse = roomService.createRoom(userId, request.roomName(), request.roomType(), request.memberIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(roomResponse);
    }

    @GetMapping
    public ResponseEntity<List<RoomSummaryResponse>> getMyRooms(@AuthenticationPrincipal Long userId) {
        List<RoomSummaryResponse> myRooms = roomService.getMyRooms(userId);
        return ResponseEntity.ok(myRooms);
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leaveRoom(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        roomService.leaveRoom(userId, id);
        return ResponseEntity.noContent().build();
    }
}
