package com.piuda.wilson.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.piuda.wilson.infrastructure.grpc.dialogue.v1.AudioRequest;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.CompletionFrame;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.DialogueServiceGrpc;
import com.piuda.wilson.infrastructure.grpc.dialogue.v1.StreamResponse;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannel;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GrpcClientConfigTest {

    private static final String SCHEME = "wilson-test-lb";

    private final Map<String, AtomicInteger> callsByServer = new ConcurrentHashMap<>();
    private final List<Server> servers = new ArrayList<>();
    private final StaticResolverProvider resolverProvider = new StaticResolverProvider();
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        servers.add(startServer("ai-pod-1"));
        servers.add(startServer("ai-pod-2"));
        resolverProvider.addresses = servers.stream()
                .map(server -> new EquivalentAddressGroup(new InetSocketAddress("127.0.0.1", server.getPort())))
                .toList();
        NameResolverRegistry.getDefaultRegistry().register(resolverProvider);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        servers.forEach(Server::shutdownNow);
        NameResolverRegistry.getDefaultRegistry().deregister(resolverProvider);
    }

    private Server startServer(String name) throws Exception {
        callsByServer.put(name, new AtomicInteger());
        return NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(new DialogueServiceGrpc.DialogueServiceImplBase() {
                    @Override
                    public void converse(AudioRequest request, StreamObserver<StreamResponse> observer) {
                        callsByServer.get(name).incrementAndGet();
                        observer.onNext(StreamResponse.newBuilder()
                                .setCompletion(CompletionFrame.newBuilder().setFullLlmResponseText(name)).build());
                        observer.onCompleted();
                    }
                })
                .build()
                .start();
    }

    private static GrpcDialogueProperties properties(String loadBalancingPolicy) {
        return new GrpcDialogueProperties(SCHEME + ":///orchestrator", loadBalancingPolicy,
                Duration.ofSeconds(1), Duration.ofSeconds(3), 3, Duration.ofMillis(10), "ko-KR");
    }

    private void callTimes(int times) {
        DialogueServiceGrpc.DialogueServiceBlockingStub stub = DialogueServiceGrpc.newBlockingStub(channel);
        for (int i = 0; i < times; i++) {
            stub.converse(AudioRequest.newBuilder().setTurnId("turn-" + i).build()).forEachRemaining(frame -> {
            });
        }
    }

    /** round_robin은 연결이 준비된 서버끼리만 번갈아 고르므로, 두 서버 연결이 모두 준비될 때까지 호출해 둔다. */
    private void warmUpUntilAllServersReached() {
        for (int i = 0; i < 100 && callsByServer.values().stream().anyMatch(count -> count.get() == 0); i++) {
            callTimes(1);
        }
        callsByServer.values().forEach(count -> count.set(0));
    }

    @Test
    @DisplayName("이름 해석으로 받은 AI 서버 여러 대에 요청을 번갈아 보낸다 (round_robin)")
    void roundRobinAcrossResolvedServers() {
        // given
        channel = new GrpcClientConfig().aiDialogueChannel(properties("round_robin"));
        warmUpUntilAllServersReached();

        // when
        callTimes(6);

        // then
        assertThat(callsByServer.get("ai-pod-1")).hasValue(3);
        assertThat(callsByServer.get("ai-pod-2")).hasValue(3);
    }

    @Test
    @DisplayName("gRPC 기본 정책(pick_first)이면 연결 하나를 재사용해 요청이 서버 한 대로만 몰린다")
    void pickFirstSticksToOneServer() {
        // given
        channel = new GrpcClientConfig().aiDialogueChannel(properties("pick_first"));
        warmUpUntilAllServersReached();

        // when
        callTimes(6);

        // then
        assertThat(callsByServer.values()).extracting(AtomicInteger::get).containsExactlyInAnyOrder(6, 0);
    }

    /** 테스트용 이름 해석기. 실제 운영에서는 dns:/// 해석기가 headless Service의 Pod IP 목록을 돌려준다. */
    private static class StaticResolverProvider extends NameResolverProvider {

        private volatile List<EquivalentAddressGroup> addresses = List.of();

        @Override
        protected boolean isAvailable() {
            return true;
        }

        @Override
        protected int priority() {
            return 5;
        }

        @Override
        public String getDefaultScheme() {
            return SCHEME;
        }

        @Override
        public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
            if (!SCHEME.equals(targetUri.getScheme())) {
                return null;
            }
            return new NameResolver() {
                @Override
                public String getServiceAuthority() {
                    return "orchestrator";
                }

                @Override
                public void start(Listener2 listener) {
                    listener.onResult(ResolutionResult.newBuilder().setAddresses(addresses).build());
                }

                @Override
                public void shutdown() {
                }
            };
        }
    }
}
