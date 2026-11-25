package com.lcq.aiagent.chat.service.impl;

import com.lcq.aiagent.chat.dto.response.ChatResponseDTO;
import com.lcq.aiagent.chat.service.BasicChatService;
import com.lcq.aiagent.chat.sse.ChatEventType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.lcq.aiagent.chat.sse.SSEUtil.event;

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
    @Deprecated
    public Flux<String> benchmarkStream(String message) {
        return buildRequest(message).stream().content();
    }

    @Override
    public Flux<ServerSentEvent<Map<String, Object>>> benchmarkEventStream(String message) {
        // 复用现有逻辑：校验问题，组装本轮请求。
        var request = buildRequest(message);

        // 正文流：模型每返回一段文字，就包装成一条 answer.delta。
        var answer = Flux.defer(() -> request.stream().content())
                .map(text -> event(ChatEventType.ANSWER_DELTA, Map.of("text", text)))
                .switchIfEmpty(Flux.error(new IllegalStateException("模型没有返回正文")));

        // 整轮事件流：两个开始事件 → 多个正文片段 → 一个结束事件。
        return answer
                .startWith(
                        event(ChatEventType.TURN_STARTED, Map.of()),
                        event(ChatEventType.GENERATION_STARTED, Map.of()))
                .concatWithValues(
                        event(ChatEventType.TURN_FINISHED, Map.of("status", "completed")))
                .onErrorReturn(
                        event(ChatEventType.TURN_FINISHED, Map.of("status", "failed")));
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
