package com.lcq.aiagent.chat.exception;

public class EmptyModelResponseException extends IllegalStateException {

    public EmptyModelResponseException() {
        super("模型没有返回正文");
    }
}
