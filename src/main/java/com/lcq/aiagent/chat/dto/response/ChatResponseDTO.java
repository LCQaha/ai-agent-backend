package com.lcq.aiagent.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class ChatResponseDTO {

    public String message;
    public String conversationId;
}
