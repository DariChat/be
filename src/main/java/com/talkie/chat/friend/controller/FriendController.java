package com.talkie.chat.friend.controller;

import com.talkie.chat.friend.dto.FriendRequestResponse;
import com.talkie.chat.friend.dto.FriendResponse;
import com.talkie.chat.friend.enums.FriendshipStatus;
import com.talkie.chat.friend.service.FriendService;
import com.talkie.chat.global.exception.ApiResponse;
import com.talkie.chat.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Friend", description = "친구 요청/수락/거절, 친구 목록 조회")
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "친구 요청 보내기", description = "동일 방향으로 이미 요청/친구 상태면 409. " +
            "상대가 이미 나에게 보낸 PENDING 요청이 있으면 새로 만들지 않고 그 요청을 자동 수락하며 200으로 응답한다(status=ACCEPTED).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "요청 생성 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상대의 기존 요청을 자동 수락")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본인에게 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 유저",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 요청/친구 상태",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/requests/{addresseeId}")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> sendRequest(
            @AuthenticationPrincipal Long userId, @PathVariable Long addresseeId) {

        FriendRequestResponse response = friendService.sendRequest(userId, addresseeId);
        HttpStatus status = response.status() == FriendshipStatus.ACCEPTED ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(response));
    }

    @Operation(summary = "친구 요청 수락", description = "수락 시 상대가 나에게 보낸 반대 방향 요청이 있다면 함께 정리된다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수락 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "요청을 받은 사람이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 처리된 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/requests/{friendshipId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptRequest(
            @AuthenticationPrincipal Long userId, @PathVariable Long friendshipId) {

        friendService.acceptRequest(userId, friendshipId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "친구 요청 취소/거절 또는 친구 관계 삭제",
            description = "PENDING 상태면 보낸 사람 호출 시 취소, 받은 사람 호출 시 거절로 동작한다. " +
                    "ACCEPTED 상태(이미 친구)여도 당사자면 호출해 관계를 끊을 수 있다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "요청 당사자가 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 요청 또는 관계",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/requests/{friendshipId}")
    public ResponseEntity<ApiResponse<Void>> removeRequest(
            @AuthenticationPrincipal Long userId, @PathVariable Long friendshipId) {

        friendService.removeRequest(userId, friendshipId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "받은 친구 요청 목록", description = "나에게 온 PENDING 상태 요청만 반환한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/requests/received")
    public ResponseEntity<ApiResponse<List<FriendRequestResponse>>> getReceivedRequests(
            @AuthenticationPrincipal Long userId) {

        List<FriendRequestResponse> requests = friendService.getReceivedRequests(userId);
        return ResponseEntity.ok(ApiResponse.ok(requests));
    }

    @Operation(summary = "내 친구 목록", description = "ACCEPTED 상태인 친구 목록을 반환한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getFriends(@AuthenticationPrincipal Long userId) {
        List<FriendResponse> friends = friendService.getFriends(userId);
        return ResponseEntity.ok(ApiResponse.ok(friends));
    }
}