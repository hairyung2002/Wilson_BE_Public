package com.piuda.wilson.infrastructure.push;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FcmProperties.class)
public class PushConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public PushSender pushSender(FcmProperties properties) {
        if (!properties.enabled()) {
            return new LoggingPushSender();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        return new FcmPushSender(RestClient.builder().requestFactory(requestFactory).build(),
                new GoogleFcmAccessTokenProvider(properties.credentialsPath()), properties.projectId());
    }
}
