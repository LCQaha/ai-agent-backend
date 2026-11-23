package com.lcq.aiagent.chat.exception;

/**
 * 聊天请求参数不合法时抛出的异常。
 */
public class InvalidChatRequestException extends RuntimeException {

    public InvalidChatRequestException(String message) {
        super(message);
    }
}
