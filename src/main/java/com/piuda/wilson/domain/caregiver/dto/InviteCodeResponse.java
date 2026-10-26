package com.piuda.wilson.domain.caregiver.dto;

import java.time.LocalDateTime;

public record InviteCodeResponse(String inviteCode, LocalDateTime expiresAt) {
}
