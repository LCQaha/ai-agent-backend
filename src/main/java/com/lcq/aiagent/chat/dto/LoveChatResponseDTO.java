package com.lcq.aiagent.chat.dto;

/**
 * 返回本次使用的会话 ID 和回答，供客户端继续同一会话。
 */
public record LoveChatResponseDTO(String conversationId, String answer) {
}
