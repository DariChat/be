package com.talkie.chat.room.controller;

import com.talkie.chat.global.exception.ApiResponse;
import com.talkie.chat.global.exception.ErrorResponse;
import com.talkie.chat.room.dto.RoomCreateRequest;
import com.talkie.chat.room.dto.RoomResponse;
import com.talkie.chat.room.dto.RoomSummaryResponse;
import com.talkie.chat.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Room", description = "채팅방 생성/목록 조회/퇴장")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "채팅방 생성", description = "roomType이 DIRECT면 roomName 생략 시 상대방 닉네임으로 자동 설정된다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "초대 대상이 비어있거나 GROUP인데 roomName이 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 초대 대상 포함",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(@AuthenticationPrincipal Long userId, @Valid @RequestBody RoomCreateRequest request) {
        RoomResponse roomResponse = roomService.createRoom(userId, request.roomName(), request.roomType(), request.memberIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(roomResponse));
    }

    @Operation(summary = "내 채팅방 목록 조회", description = "마지막 메시지, 안읽은 메시지 수를 함께 반환한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomSummaryResponse>>> getMyRooms(@AuthenticationPrincipal Long userId) {
        List<RoomSummaryResponse> myRooms = roomService.getMyRooms(userId);
        return ResponseEntity.ok(ApiResponse.ok(myRooms));
    }

    @Operation(summary = "채팅방 퇴장", description = "OWNER가 퇴장하면 가장 오래된 멤버에게 소유권이 이전되고, 마지막 멤버면 방이 삭제된다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "퇴장 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "방을 찾을 수 없거나 방 멤버가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{id}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        roomService.leaveRoom(userId, id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
