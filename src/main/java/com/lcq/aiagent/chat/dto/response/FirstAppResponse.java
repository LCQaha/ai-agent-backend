package com.lcq.aiagent.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

public record FirstAppResponse(UUID conversationId, String answer, Instant expiresAt) {
}
