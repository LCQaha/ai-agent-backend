package com.lcq.aiagent.chat.sse;

public enum ChatEventType {
    TURN_STARTED("turn.started"),
    GENERATION_STARTED("generation.started"),
    ANSWER_DELTA("answer.delta"),
    TURN_FINISHED("turn.finished");

    private final String eventName;

    ChatEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}