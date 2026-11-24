package com.lcq.aiagent.chat.service.impl;

import com.lcq.aiagent.chat.dto.response.ChatResponseDTO;
import com.lcq.aiagent.chat.service.BasicChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

@Service
public class BasicChatServiceimpl implements BasicChatService {

    private final ChatClient chatClient;

    public BasicChatServiceimpl(ChatClient.Builder builder,
                                @Value("${app.prompts.base01}") Resource systemPrompt) {
        this.chatClient = builder
                .defaultSystem(systemPrompt, StandardCharsets.UTF_8)
                .build();
    }

    @Override
    public ChatResponseDTO benchmark(String message) {
        String answer = buildRequest(message).call().content();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("模型没有返回有效回答");
        }
        return new FirstTurnResponse(answer);
    }

    @Override
    public Flux<String> benchmarkStream(String message) {
        return buildRequest(message).stream().content();
    }

    private ChatClient.ChatClientRequestSpec buildRequest(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        return chatClient.prompt().user(message);
    }

    public static final class FirstTurnResponse extends ChatResponseDTO {

        public FirstTurnResponse(String answer) {
            super(answer, null);
        }
    }
}
