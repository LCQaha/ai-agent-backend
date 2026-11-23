package com.lcq.aiagent.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 恋爱咨询请求；conversationId 为空时创建新会话。
 */
public record LoveChatRequestDTO(
        String conversationId,
        @NotBlank(message = "message 不能为空") String message
) {
}
