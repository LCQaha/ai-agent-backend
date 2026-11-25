package com.lcq.aiagent.chat.sse;

import org.springframework.http.codec.ServerSentEvent;

import java.util.Map;

public class SSEUtil {


    public static ServerSentEvent<Map<String, Object>> event(
            ChatEventType type, Map<String, Object> data) {
        return ServerSentEvent.builder(data)
                .event(type.eventName())
                .build();
    }
}
