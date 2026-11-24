package com.lcq.aiagent.chat.service;

import com.lcq.aiagent.chat.dto.response.ChatResponseDTO;
import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Flux;

public interface BasicChatService {

    ChatResponseDTO benchmark(String message);

    Flux<String> benchmarkStream(@NotBlank String message);
}
