package com.lcq.aiagent.chat.controller;

import com.lcq.aiagent.chat.dto.request.FirstAppRequest;
import com.lcq.aiagent.chat.dto.response.FirstAppResponse;
import com.lcq.aiagent.chat.service.FirstAppService;
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
@RequestMapping("/ai/first-app")
public class FirstAppController {

    private final FirstAppService firstAppService;

    public FirstAppController(FirstAppService firstAppService) {
        this.firstAppService = firstAppService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public FirstAppResponse chat(@Valid @RequestBody FirstAppRequest request) {
        return firstAppService.chat(request.message(), request.conversationId());
    }

    @PostMapping(value = "/event-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> chatEventStream(@Valid @RequestBody FirstAppRequest request) {
        return firstAppService.chatEventStream(request.message(), request.conversationId());
    }
}
