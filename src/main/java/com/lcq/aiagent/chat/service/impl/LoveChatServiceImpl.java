package com.lcq.aiagent.chat.service.impl;

import com.lcq.aiagent.chat.dto.LoveChatResponseDTO;
import com.lcq.aiagent.chat.exception.InvalidChatRequestException;
import com.lcq.aiagent.chat.service.LoveChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LoveChatServiceImpl implements LoveChatService {
    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    public LoveChatServiceImpl(ChatClient.Builder builder) {
        // create Memory
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(50)
                .build();

        // create MemoryAdvisor
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        SimpleLoggerAdvisor simpleLoggerAdvisor = SimpleLoggerAdvisor.builder().build();
        // create Client
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(List.of(memoryAdvisor, simpleLoggerAdvisor))
                .build();
    }

    @Override
    public LoveChatResponseDTO chat(String conversationId, String message) {
        if (message == null || message.isBlank()) {
            throw new InvalidChatRequestException("message 不能为空");
        }

        String effectiveConversationId = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString()
                : conversationId;

        String answer = chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, effectiveConversationId))
                .call()
                .content();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("模型未返回回答文本，请检查响应或拒绝信息。");
        }
        return new LoveChatResponseDTO(effectiveConversationId, answer);
    }
}
