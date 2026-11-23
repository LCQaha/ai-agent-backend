package com.lcq.aiagent.chat.controller;

import com.lcq.aiagent.chat.dto.ChatRequestDTO;
import com.lcq.aiagent.chat.dto.LoveChatRequestDTO;
import com.lcq.aiagent.chat.dto.LoveChatResponseDTO;
import com.lcq.aiagent.chat.service.LoveChatService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final ChatClient chatClient;

    private final LoveChatService loveChatService;

    public AiChatController(ChatClient.Builder builder, LoveChatService loveChatService) {
        this.loveChatService = loveChatService;
        this.chatClient = builder
                .defaultSystem("请用中文简洁回答问题。")
                .build();
    }

    @Operation(summary = "单轮 AI 对话")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_PLAIN_VALUE)
    public String chat(@Valid @RequestBody ChatRequestDTO request) {
        String answer = chatClient.prompt()
                .user(request.message())
                .call()
                .content();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("模型未返回回答文本，请检查响应或拒绝信息。");
        }
        return answer;
    }

    @Operation(summary = "恋爱咨询（多轮对话）",
            description = "首次请求可不传 conversationId；后续请求携带响应中的 conversationId 继续对话。")
    @PostMapping(value = "/love/v1", produces = MediaType.APPLICATION_JSON_VALUE)
    public LoveChatResponseDTO chatWithLove(@Valid @RequestBody LoveChatRequestDTO request) {
        return loveChatService.chat(request.conversationId(), request.message());
    }

}
