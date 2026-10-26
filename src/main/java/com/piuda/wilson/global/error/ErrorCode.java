package com.piuda.wilson.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C001", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C002", "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C003", "허용되지 않은 메서드입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "요청한 리소스를 찾을 수 없습니다."),
    NO_PERMISSION(HttpStatus.FORBIDDEN, "C005", "접근 권한이 없습니다."),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "M001", "이미 사용 중인 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M002", "회원 정보를 찾을 수 없습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "M003", "현재 비밀번호가 일치하지 않습니다."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "M004", "새 비밀번호가 현재 비밀번호와 같습니다."),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 리프레시 토큰입니다."),
    INVALID_DEVICE_CODE(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않거나 만료된 기기 연결 코드입니다."),
    TOO_MANY_DEVICE_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "A004", "시도 횟수가 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    ELDERLY_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "노인 정보를 찾을 수 없습니다."),
    DEVICE_CODE_ISSUE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "E002", "기기 연결 코드를 발급하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    INVITE_CODE_ISSUE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "E003", "초대 코드를 발급하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    CAREGIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "G001", "보호자 정보를 찾을 수 없습니다."),
    DUPLICATE_CARE_RELATIONSHIP(HttpStatus.CONFLICT, "G002", "이미 연결된 보호자-노인 관계입니다."),
    INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "G003", "잘못되었거나 만료된 초대 코드입니다."),
    TOO_MANY_INVITE_CODE_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "G004", "시도 횟수가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    OWNER_TRANSFER_REQUIRED(HttpStatus.CONFLICT, "G005", "주보호자를 먼저 다른 보호자에게 넘겨 주세요."),
    INVALID_OWNER_TRANSFER_TARGET(HttpStatus.BAD_REQUEST, "G006", "주보호자를 넘길 수 없는 대상입니다."),
    CARE_RELATIONSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "G007", "보호자 연결을 찾을 수 없습니다."),

    REQUIRED_CONSENT_MISSING(HttpStatus.BAD_REQUEST, "CS001", "민감정보 처리 동의가 필요합니다."),
    REQUIRED_CONSENT_NOT_REVOCABLE(HttpStatus.BAD_REQUEST, "CS002", "필수 동의는 철회할 수 없습니다. 노인 삭제로 처리해 주세요."),

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."),

    EMERGENCY_NOT_FOUND(HttpStatus.NOT_FOUND, "EM001", "응급 기록을 찾을 수 없습니다."),

    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SC001", "일정을 찾을 수 없습니다."),
    DUPLICATE_SCHEDULE_TIME(HttpStatus.CONFLICT, "SC002", "같은 시각에 요일이 겹치는 일정이 이미 있습니다."),
    PROACTIVE_TRIGGER_NOT_FOUND(HttpStatus.NOT_FOUND, "SC003", "선제 대화 요청을 찾을 수 없습니다."),
    PROACTIVE_TRIGGER_UNAVAILABLE(HttpStatus.CONFLICT, "SC004", "이미 지나간 선제 대화 요청입니다."),

    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CV001", "대화를 찾을 수 없습니다."),
    CONVERSATION_ALREADY_FINISHED(HttpStatus.CONFLICT, "CV002", "이미 종료된 대화입니다."),
    CONVERSATION_TURN_IN_PROGRESS(HttpStatus.CONFLICT, "CV003", "이전 대화 응답을 처리하고 있습니다."),
    EMPTY_AUDIO(HttpStatus.BAD_REQUEST, "CV004", "음성 데이터가 비어 있습니다."),
    VOICE_PROTOCOL_VIOLATION(HttpStatus.BAD_REQUEST, "CV005", "음성 전송 순서나 형식이 올바르지 않습니다."),
    AUDIO_TOO_LARGE(HttpStatus.BAD_REQUEST, "CV006", "음성 데이터가 너무 큽니다."),

    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI001", "AI 서버 처리 중 오류가 발생했습니다."),
    AI_SERVER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI002", "AI 서버 응답이 지연되고 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
