package com.talkie.chat.user.controller;

import com.talkie.chat.global.exception.ErrorResponse;
import com.talkie.chat.user.dto.UserResponse;
import com.talkie.chat.user.dto.PasswordUpdateRequest;
import com.talkie.chat.user.dto.UserUpdateRequest;
import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "내 프로필 조회/수정")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 프로필 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "인증되지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal Long id) {
        User user = userService.getUser(id);

        return ResponseEntity.ok(UserResponse.from(user));
    }

    @Operation(summary = "프로필 수정", description = "닉네임과 프로필 이미지 URL을 변경한다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "409", description = "닉네임 중복",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/update")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal Long id, @Valid @RequestBody UserUpdateRequest request) {

        User user = userService.updateProfile(
                id, request.nickname(), request.profileImageUrl()
        );

        return ResponseEntity.ok(UserResponse.from(user));
    }

    @Operation(summary = "비밀번호 변경")
    @ApiResponse(responseCode = "200", description = "변경 성공")
    @ApiResponse(responseCode = "401", description = "인증되지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/update/password")
    public ResponseEntity<UserResponse> updatePassword(
            @AuthenticationPrincipal Long id, @Valid @RequestBody PasswordUpdateRequest request
            ) {
        User user = userService.updatePassword(id, request.password());

        return ResponseEntity.ok(UserResponse.from(user));
    }

}
