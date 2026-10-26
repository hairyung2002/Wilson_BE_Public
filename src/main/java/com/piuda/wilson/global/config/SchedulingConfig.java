package com.piuda.wilson.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 예약 작업 설정. 일정·방해 금지 시간·일별 집계는 KST 기준이다 (api-spec 0-2). */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(KST);
    }
}
