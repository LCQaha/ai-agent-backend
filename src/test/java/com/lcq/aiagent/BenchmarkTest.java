package com.lcq.aiagent;

import com.lcq.aiagent.chat.dto.response.ChatResponseDTO;
import com.lcq.aiagent.chat.service.BasicChatService;
import jakarta.annotation.Resource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class BenchmarkTest {

    @Resource
    private BasicChatService basicChatService;

    static List<String> questions() {
        String customMessage = System.getProperty("benchmark.message");
        if (customMessage != null) {
            return List.of(customMessage);
        }
        return List.of("你好，请介绍一下自己", "什么是 Spring AI？", "解释一下 RAG 的工作原理");
    }

    @ParameterizedTest
    @MethodSource("questions")
    public void benchmarkTest(String question) {
        ChatResponseDTO response = basicChatService.benchmark(question);
        assertNotNull(response, "BaseChat 应返回响应");
        assertTrue(StringUtils.hasText(response.getMessage()), "BaseChat 应返回非空回答");
        System.out.println(response.getMessage());
    }
}
