package com.piuda.wilson.domain.elderly.dto;

import java.time.LocalDateTime;

public record DeviceCodeResponse(String deviceCode, LocalDateTime expiresAt) {
}
