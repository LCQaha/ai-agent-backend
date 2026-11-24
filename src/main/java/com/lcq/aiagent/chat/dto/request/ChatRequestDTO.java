package com.lcq.aiagent.chat.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 聊天请求参数。
 */
public record ChatRequestDTO(@NotBlank String message) {
}
