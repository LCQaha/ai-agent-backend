package com.lcq.aiagent.chat.service;

import com.lcq.aiagent.chat.dto.response.ChatResponseDTO;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Map;

public interface BasicChatService {

    ChatResponseDTO benchmark(String message);

    Flux<String> benchmarkStream(@NotBlank String message);

    Flux<ServerSentEvent<Map<String, Object>>> benchmarkEventStream(String message);
}
