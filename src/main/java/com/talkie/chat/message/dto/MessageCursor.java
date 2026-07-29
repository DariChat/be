package com.talkie.chat.message.dto;

import java.time.LocalDateTime;

/**
 * 커서 페이지네이션 키. created_at 단독 커서는 동일 시각에 걸친 메시지를 다음 페이지에서
 * 영구히 누락시킬 수 있어(같은 초/마이크로초에 여러 메시지가 쌓이는 경우), id를 타이브레이커로
 * 함께 사용해 (created_at, id) 기준으로 결정적인 정렬/커서링을 보장한다.
 */
public record MessageCursor(
        LocalDateTime createdAt,
        Long id
) {
}
