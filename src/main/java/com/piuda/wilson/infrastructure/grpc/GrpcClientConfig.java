package com.piuda.wilson.infrastructure.grpc;

import com.piuda.wilson.infrastructure.ai.DialogueClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GrpcDialogueProperties.class)
public class GrpcClientConfig {

    /**
     * AI 서버와는 내부망 h2c 통신이다. 외부망을 거치게 되면 TLS로 전환한다.
     *
     * <p>gRPC는 HTTP/2 연결 하나를 재사용하므로 ClusterIP(L4)로는 요청이 Pod 하나에 몰린다.
     * headless Service를 {@code dns:///}로 해석해 Pod 주소를 모두 받고, 클라이언트가 요청마다 Pod를 번갈아 고른다.</p>
     */
    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel aiDialogueChannel(GrpcDialogueProperties properties) {
        return NettyChannelBuilder.forTarget(properties.target())
                .defaultLoadBalancingPolicy(properties.loadBalancingPolicy())
                .usePlaintext()
                .build();
    }

    @Bean
    public CircuitBreaker aiDialogueCircuitBreaker() {
        return CircuitBreaker.of("ai-dialogue", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService aiDialogueScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ai-dialogue-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public DialogueClient dialogueClient(ManagedChannel aiDialogueChannel, CircuitBreaker aiDialogueCircuitBreaker,
                                         ScheduledExecutorService aiDialogueScheduler,
                                         GrpcDialogueProperties properties) {
        return new GrpcDialogueClient(aiDialogueChannel, aiDialogueCircuitBreaker, aiDialogueScheduler, properties);
    }
}
