package com.lcq.aiagent.chat.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record FirstAppRequest(@NotBlank String message, UUID conversationId) {
}
