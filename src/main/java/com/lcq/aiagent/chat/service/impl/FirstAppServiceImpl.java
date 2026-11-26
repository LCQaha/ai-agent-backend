package com.lcq.aiagent.chat.service.impl;

import com.lcq.aiagent.chat.dto.response.FirstAppResponse;
import com.lcq.aiagent.chat.exception.EmptyModelResponseException;
import com.lcq.aiagent.chat.service.FirstAppService;
import com.lcq.aiagent.chat.sse.ChatEventType;
import com.lcq.aiagent.chat.sse.FirstAppStreamError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.session.compaction.ChatModelSummarizer;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static com.lcq.aiagent.chat.sse.SSEUtil.event;

@Slf4j
@Service
public class FirstAppServiceImpl implements FirstAppService {

    private final ChatClient chatClient;
    private final SessionService sessionService;

    public FirstAppServiceImpl(ChatClient.Builder builder, SessionService sessionService,
                               @Value("${app.prompts.first-app}") Resource systemPrompt,
                               @Value("${app.ai.memory.trigger-turns}") int triggerTurns,
                               @Value("${app.ai.memory.keep-turns}") int keepTurns,
                               ChatModelSummarizer chatModelSummarizer) {
        Assert.isTrue(keepTurns > 0 && keepTurns < triggerTurns,
                "会话压缩参数须满足 0 < keep-turns < trigger-turns");
        this.sessionService = sessionService;
        var trigger = new TurnCountTrigger(triggerTurns);
        var window = WindowCompactionStrategy.builder()
                .lastTurns(keepTurns)
                .summarizer(chatModelSummarizer)
                .build();
        this.chatClient = builder
                .defaultSystem(systemPrompt, StandardCharsets.UTF_8)
                .defaultAdvisors(SessionAdvisors.builder(sessionService)
                        .compaction(trigger, window)
                        .asyncCompaction(true)
                        .build())
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build())
                .build();
    }

    @Override
    public FirstAppResponse chat(String message, UUID conversationId) {
        Assert.hasText(message, "问题不能为空");
        var session = getOrCreateSession(conversationId);
        String answer = buildRequest(message, session.id()).call().content();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("模型没有返回有效回答");
        }
        return new FirstAppResponse(UUID.fromString(session.id()), answer, session.expiresAt());
    }

    @Override
    public Flux<ServerSentEvent<Map<String, Object>>> chatEventStream(String message, UUID conversationId) {
        Assert.hasText(message, "问题不能为空");
        return Flux.defer(() -> {
            var session = getOrCreateSession(conversationId);
            var request = buildRequest(message, session.id());
            Map<String, Object> started = session.expiresAt() == null
                    ? Map.of("conversationId", session.id())
                    : Map.of("conversationId", session.id(), "expiresAt", session.expiresAt().toString());
            return Flux.defer(() -> request.stream().content())
                    .map(text -> event(ChatEventType.ANSWER_DELTA, Map.of("text", text)))
                    .switchIfEmpty(Flux.error(EmptyModelResponseException::new))
                    .startWith(event(ChatEventType.TURN_STARTED, started),
                            event(ChatEventType.GENERATION_STARTED, Map.of()))
                    .concatWithValues(event(ChatEventType.TURN_FINISHED, Map.of("status", "completed")))
                    .doOnError(error -> log.warn("FirstApp 流式回答失败，conversationId={}", session.id(), error));
        }).onErrorResume(error -> Flux.just(event(ChatEventType.TURN_FINISHED,
                Map.of("status", "failed", "error", FirstAppStreamError.from(error)))));
    }

    private Session getOrCreateSession(UUID conversationId) {
        if (conversationId == null) {
            return sessionService.create(CreateSessionRequest.builder().build());
        }
        return sessionService.findOrCreate(CreateSessionRequest.builder().id(conversationId.toString()).build());
    }

    private ChatClient.ChatClientRequestSpec buildRequest(String message, String conversationId) {
        return chatClient.prompt()
                .user(message)
                .advisors(SessionAdvisors.session(conversationId));
    }
}
