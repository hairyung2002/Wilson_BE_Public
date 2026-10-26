package com.piuda.wilson.domain.auth.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class DeviceCodeGenerator {

    private static final int CODE_BOUND = 1_000_000;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return String.format("%06d", random.nextInt(CODE_BOUND));
    }
}
