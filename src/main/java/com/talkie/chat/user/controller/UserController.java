package com.talkie.chat.user.controller;

import com.talkie.chat.global.exception.ApiResponse;
import com.talkie.chat.global.exception.ErrorResponse;
import com.talkie.chat.user.dto.UserResponse;
import com.talkie.chat.user.dto.PasswordUpdateRequest;
import com.talkie.chat.user.dto.UserSearchResponse;
import com.talkie.chat.user.dto.UserUpdateRequest;
import com.talkie.chat.user.entity.User;
import com.talkie.chat.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User", description = "내 프로필 조회/수정, 유저 검색")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 프로필 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(@AuthenticationPrincipal Long id) {
        User user = userService.getUser(id);

        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @Operation(summary = "프로필 수정", description = "닉네임과 프로필 이미지 URL을 변경한다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "닉네임 중복",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/update")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal Long id, @Valid @RequestBody UserUpdateRequest request) {

        User user = userService.updateProfile(
                id, request.nickname(), request.profileImageUrl(), request.preferredLanguage()
        );

        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @Operation(summary = "비밀번호 변경")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/update/password")
    public ResponseEntity<ApiResponse<UserResponse>> updatePassword(
            @AuthenticationPrincipal Long id, @Valid @RequestBody PasswordUpdateRequest request
            ) {
        User user = userService.updatePassword(id, request.password());

        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @Operation(summary = "유저 검색", description = "닉네임 부분일치로 유저를 검색한다. 본인은 결과에서 제외된다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검색어 누락",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserSearchResponse>>> searchUsers(
            @AuthenticationPrincipal Long userId,
            @RequestParam String keyword,
            @Parameter(description = "이전 페이지 마지막 유저의 닉네임 (없으면 첫 페이지)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (1~100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        List<UserSearchResponse> users = userService.searchUsers(userId, keyword, cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

}
