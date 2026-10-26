package com.piuda.wilson.infrastructure.grpc;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서버 gRPC 대화 채널 설정.
 *
 * @param target              gRPC 대상. 운영은 {@code dns:///<headless-service>:<port>}로 AI Pod 주소 전체를 받는다
 * @param loadBalancingPolicy 받은 주소들에 요청을 나누는 방식. gRPC는 연결 하나를 재사용하므로
 *                            L4 로드밸런서가 아닌 클라이언트에서 나눠야 한다 ({@code round_robin})
 * @param firstFrameTimeout   첫 프레임까지 기다리는 시간. 넘으면 호출을 취소하고 재시도한다
 * @param streamDeadline      스트림 전체 제한 시간
 * @param maxAttempts         첫 프레임 전 실패 시 최대 시도 횟수 (최초 호출 포함)
 * @param initialBackoff      재시도 대기 시간. 시도마다 두 배로 늘어난다
 */
@ConfigurationProperties("wilson.ai.grpc")
public record GrpcDialogueProperties(String target, String loadBalancingPolicy, Duration firstFrameTimeout,
                                     Duration streamDeadline, int maxAttempts, Duration initialBackoff,
                                     String languageCode) {
}
