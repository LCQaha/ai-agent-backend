package com.lcq.aiagent.chat.controller;

import com.lcq.aiagent.chat.dto.ChatRequestDTO;
import com.lcq.aiagent.chat.dto.ChatResponseDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/ai")
public class ChatController {
    @RequestMapping("/benchmark")
    public ChatResponseDTO benchmark(ChatRequestDTO request){
        //todo
        return null;
    }
}
