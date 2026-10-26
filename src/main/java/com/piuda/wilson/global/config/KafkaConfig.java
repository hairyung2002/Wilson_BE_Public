package com.piuda.wilson.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    /**
     * 수신 처리 중 예외(주로 DB 장애)가 나면 1초부터 두 배씩, 최대 30초 간격으로 10분까지 다시 시도한다.
     * 형식 오류 메시지는 수신기에서 이미 걸러내므로 여기까지 오지 않는다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxElapsedTime(600_000L);
        return new DefaultErrorHandler(backOff);
    }
}
