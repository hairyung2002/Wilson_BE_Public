package com.piuda.wilson.domain.caregiver.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 보호자 초대 코드 생성기. 전화로 불러 주기 쉽도록 헷갈리는 문자(0·O·1·I)를 뺀 32자로 8자리를 만든다 (약 1조 가지). */
@Component
public class InviteCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
