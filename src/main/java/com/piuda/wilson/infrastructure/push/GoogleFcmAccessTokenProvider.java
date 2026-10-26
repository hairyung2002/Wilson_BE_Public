package com.piuda.wilson.infrastructure.push;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** 서비스 계정 JSON(K8s Secret 파일 마운트)으로 FCM 액세스 토큰을 받는다. 만료 전이면 캐시된 토큰을 쓴다. */
public class GoogleFcmAccessTokenProvider implements FcmAccessTokenProvider {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final GoogleCredentials credentials;

    public GoogleFcmAccessTokenProvider(String credentialsPath) {
        try (InputStream in = new FileInputStream(credentialsPath)) {
            this.credentials = GoogleCredentials.fromStream(in).createScoped(List.of(SCOPE));
        } catch (IOException e) {
            throw new UncheckedIOException("FCM 서비스 계정 파일을 읽을 수 없습니다.", e);
        }
    }

    @Override
    public synchronized String accessToken() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new IllegalStateException("FCM 액세스 토큰을 받지 못했습니다.", e);
        }
    }
}
