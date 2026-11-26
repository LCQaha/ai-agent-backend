package com.lcq.aiagent.ai.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.ChatModelSummarizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ModelConfiguration {

    @Bean
    public SessionService sessionService() {
        return DefaultSessionService.builder()
                .sessionRepository(InMemorySessionRepository.builder().build())
                .build();
    }

    @Bean
    public ChatModelSummarizer chatModelSummarizer(
            ChatModel chatModel) {

        return ChatModelSummarizer.builder(chatModel)
                .overlapSize(2)
                .build();
    }
}
