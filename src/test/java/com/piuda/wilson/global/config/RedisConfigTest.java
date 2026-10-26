package com.piuda.wilson.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

class RedisConfigTest {

    private final RedisTemplate<String, Object> redisTemplate =
            new RedisConfig().redisTemplate(mock(RedisConnectionFactory.class));

    @Test
    void 문자열_값은_저장했다가_읽으면_같은_문자열로_돌아온다() {
        // given
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();

        // when
        Object restored = valueSerializer.deserialize(valueSerializer.serialize("1729400000"));

        // then
        assertThat(restored).isEqualTo("1729400000");
    }

    @Test
    void 키는_접두사가_그대로_보이도록_문자열로_저장한다() {
        // given
        @SuppressWarnings("unchecked")
        RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();

        // when
        byte[] key = keySerializer.serialize("token-revoked:1");

        // then
        assertThat(new String(key)).isEqualTo("token-revoked:1");
    }
}
