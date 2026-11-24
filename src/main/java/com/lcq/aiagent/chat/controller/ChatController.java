package com.lcq.aiagent.chat.controller;

import com.lcq.aiagent.chat.dto.request.ChatRequestDTO;
import com.lcq.aiagent.chat.dto.response.ChatResponseDTO;
import com.lcq.aiagent.chat.service.BasicChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
}
