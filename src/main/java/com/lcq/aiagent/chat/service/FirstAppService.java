package com.lcq.aiagent.chat.service;

import com.lcq.aiagent.chat.dto.response.FirstAppResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

public interface FirstAppService {

    FirstAppResponse chat(String message, UUID conversationId);

    Flux<ServerSentEvent<Map<String, Object>>> chatEventStream(String message, UUID conversationId);
}
