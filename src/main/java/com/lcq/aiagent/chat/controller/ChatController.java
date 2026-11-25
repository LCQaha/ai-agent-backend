package com.lcq.aiagent.chat.controller;

import com.lcq.aiagent.chat.dto.request.ChatRequestDTO;
import com.lcq.aiagent.chat.dto.response.ChatResponseDTO;
import com.lcq.aiagent.chat.service.BasicChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final BasicChatService basicChatService;

    public ChatController(BasicChatService basicChatService){
        this.basicChatService = basicChatService;
    }

    @PostMapping("/benchmark")
    public ChatResponseDTO benchmark(@Valid @RequestBody ChatRequestDTO request) {
        return basicChatService.benchmark(request.message());
    }

    @PostMapping(value = "/benchmark-stream", produces = "text/plain;charset=UTF-8")
    public Flux<String> benchmarkStream(@Valid @RequestBody ChatRequestDTO request) {
        return basicChatService.benchmarkStream(request.message());
    }

    @PostMapping(value = "/benchmark-event-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Map<String, Object>>> benchmarkEventStream(@Valid @RequestBody ChatRequestDTO request) {
        return basicChatService.benchmarkEventStream(request.message());
    }


}
