package com.piuda.wilson.infrastructure.kafka;

import com.piuda.wilson.infrastructure.ai.AiEventPublisher;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * BE → AI 이벤트 발행 설정 (결정 J8: value는 Protobuf 바이트).
 *
 * <p>턴 분석 요청에는 노인 발화 음성(최대 10MB, WebSocket 한도와 같음)이 실리므로 요청·토픽 메시지 크기를 그보다
 * 넉넉히 둔다. 음성·대화 원문이 담긴 토픽은 24시간만 보관한다. 토픽은 {@code spring.kafka.admin.auto-create}가
 * 켜져 있을 때 앱이 만든다 (로컬은 꺼 둔다).</p>
 */
@Configuration
@EnableConfigurationProperties(AiEventProperties.class)
public class AiEventKafkaConfig {

    static final int MAX_MESSAGE_BYTES = 12 * 1024 * 1024;
    private static final Duration RAW_DATA_RETENTION = Duration.ofHours(24);
    private static final Duration ERASURE_RETENTION = Duration.ofDays(7);
    /** Kafka가 내려가 있을 때 대화 스레드가 메타데이터를 기다리며 오래 멈추지 않게 한다. */
    private static final int MAX_BLOCK_MILLIS = 5_000;

    @Bean
    public ProducerFactory<String, byte[]> aiEventProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildProducerProperties();
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, MAX_MESSAGE_BYTES);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, MAX_BLOCK_MILLIS);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, byte[]> aiEventKafkaTemplate(ProducerFactory<String, byte[]> aiEventProducerFactory) {
        return new KafkaTemplate<>(aiEventProducerFactory);
    }

    @Bean
    public AiEventPublisher aiEventPublisher(KafkaTemplate<String, byte[]> aiEventKafkaTemplate,
                                             AiEventProperties properties) {
        if (!properties.enabled()) {
            return new LoggingAiEventPublisher();
        }
        return new KafkaAiEventPublisher(aiEventKafkaTemplate, properties);
    }

    @Bean
    public KafkaAdmin.NewTopics aiEventTopics(AiEventProperties properties) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(properties.turnAnalysisTopic())
                        .partitions(properties.partitions()).replicas(properties.replicas())
                        .config(TopicConfig.RETENTION_MS_CONFIG, millis(RAW_DATA_RETENTION))
                        .config(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, String.valueOf(MAX_MESSAGE_BYTES))
                        .build(),
                TopicBuilder.name(properties.conversationEndedTopic())
                        .partitions(properties.partitions()).replicas(properties.replicas())
                        .config(TopicConfig.RETENTION_MS_CONFIG, millis(RAW_DATA_RETENTION))
                        .build(),
                TopicBuilder.name(properties.subjectErasureTopic())
                        .partitions(properties.partitions()).replicas(properties.replicas())
                        .config(TopicConfig.RETENTION_MS_CONFIG, millis(ERASURE_RETENTION))
                        .build());
    }

    private static String millis(Duration duration) {
        return String.valueOf(duration.toMillis());
    }
}
