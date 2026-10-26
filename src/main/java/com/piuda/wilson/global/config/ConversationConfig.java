package com.piuda.wilson.global.config;

import com.piuda.wilson.domain.conversation.service.ConversationContextProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConversationContextProperties.class)
public class ConversationConfig {
}
