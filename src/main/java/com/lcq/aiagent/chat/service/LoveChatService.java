package com.lcq.aiagent.chat.service;

import com.lcq.aiagent.chat.dto.LoveChatResponseDTO;

/**
 * 恋爱咨询对话服务。
 */
public interface LoveChatService {

    LoveChatResponseDTO chat(String conversationId, String message);
}
