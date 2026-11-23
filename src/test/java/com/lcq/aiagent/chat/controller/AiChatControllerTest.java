package com.lcq.aiagent.chat.controller;

import com.lcq.aiagent.chat.dto.LoveChatRequestDTO;
import com.lcq.aiagent.chat.service.impl.LoveChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用真实 Controller、Service 和记忆组件，仅替换模型；不读取密钥或访问模型服务。
 */
class AiChatControllerTest {
    private static final String LOVE_CHAT_URL = "/api/ai/love/v1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RecordingChatModel model;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        model = new RecordingChatModel();
        LoveChatServiceImpl service = new LoveChatServiceImpl(ChatClient.builder(model));
        // 分别创建 Builder，避免将恋爱客户端的记忆配置带入单轮客户端。
        AiChatController controller = new AiChatController(ChatClient.builder(model), service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                // standaloneSetup 不加载 Boot 的转换器配置，显式对齐其 UTF-8 默认值。
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new JacksonJsonHttpMessageConverter(JSON))
                .build();
    }

    @Test
    void shouldCreateConversationContinueItAndIsolateANewOne() throws Exception {
        String firstBody = mvc.perform(post(LOVE_CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"我叫小李\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer").value("模拟回答1"))
                .andReturn().getResponse().getContentAsString();

        String conversationId = JSON.readTree(firstBody).get("conversationId").asText();
        assertEquals(conversationId, UUID.fromString(conversationId).toString());

        mvc.perform(post(LOVE_CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                new LoveChatRequestDTO(conversationId, "我叫什么名字？"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId))
                .andExpect(jsonPath("$.answer").value("模拟回答2"));

        List<Message> secondPrompt = model.prompts.get(1).getInstructions();
        assertEquals(List.of(MessageType.SYSTEM, MessageType.USER,
                        MessageType.ASSISTANT, MessageType.USER),
                secondPrompt.stream().map(Message::getMessageType).toList());
        assertEquals("我叫小李", secondPrompt.get(1).getText());
        assertEquals("模拟回答1", secondPrompt.get(2).getText());
        assertEquals("我叫什么名字？", secondPrompt.get(3).getText());

        String newBody = mvc.perform(post(LOVE_CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"开始另一个会话\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNotEquals(conversationId, JSON.readTree(newBody).get("conversationId").asText());
        assertEquals(2, model.prompts.get(2).getInstructions().size());
        assertEquals("开始另一个会话", model.prompts.get(2).getUserMessage().getText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldCreateConversationForBlankId(String blankId) throws Exception {
        String body = mvc.perform(post(LOVE_CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new LoveChatRequestDTO(blankId, "你好"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = JSON.readTree(body).get("conversationId").asText();
        assertEquals(id, UUID.fromString(id).toString());
        assertEquals(1, model.prompts.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"message\":null}", "{\"message\":\"\"}", "{\"message\":\"   \"}"})
    void shouldRejectMissingOrBlankMessageBeforeCallingModel(String body) throws Exception {
        mvc.perform(post(LOVE_CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        assertTrue(model.prompts.isEmpty());
    }

    @Test
    void singleRoundEndpointShouldRemainPlainTextWithoutLoveHistory() throws Exception {
        mvc.perform(post(LOVE_CHAT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"我叫小李\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("模拟回答2"));
        assertEquals(2, model.prompts.get(1).getInstructions().size());
        assertEquals("你好", model.prompts.get(1).getUserMessage().getText());
    }

    private static final class RecordingChatModel implements ChatModel {
        private final List<Prompt> prompts = new ArrayList<>();

        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            AssistantMessage answer = new AssistantMessage("模拟回答" + prompts.size());
            return new ChatResponse(List.of(new Generation(answer)));
        }
    }
}
