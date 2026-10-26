package com.piuda.wilson.infrastructure.push;

/** FCM HTTP v1 호출용 OAuth2 액세스 토큰. 만료 전에 알아서 새로 받는다. */
@FunctionalInterface
public interface FcmAccessTokenProvider {

    String accessToken();
}
