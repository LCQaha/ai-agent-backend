package com.lcq.aiagent;

import com.lcq.aiagent.ai.config.ModelConfiguration;
import com.lcq.aiagent.chat.service.FirstAppService;
import com.lcq.aiagent.chat.service.impl.FirstAppServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.ChatModelSummarizer;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FirstAppMemoryTest {

    private final ChatModel model = mock(ChatModel.class);
    private final ChatModel summaryModel = mock(ChatModel.class);
    private final ModelConfiguration configuration = new ModelConfiguration();
    private final SessionService memory = configuration.sessionService();

    @Test
    void sharesHistoryAcrossReplyModesAndIsolatesNewConversations() {
        when(model.call(any(Prompt.class))).thenReturn(reply("收到"));
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(reply("青"), reply("松")));
        FirstAppService service = serviceWithWindow(20, 10);

        var first = service.chat("记住我的代号是青松", null);
        assertThat(first.expiresAt()).isEqualTo(memory.findById(first.conversationId().toString()).expiresAt());
        var events = service.chatEventStream("我的代号是什么？", first.conversationId())
                .collectList().block(Duration.ofSeconds(5));
        assertThat(events.get(0).event()).isEqualTo("turn.started");
        assertThat(events.get(0).data()).containsEntry("expiresAt", first.expiresAt().toString());
        var lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent).isNotNull();
        assertThat(lastEvent.event()).isEqualTo("turn.finished");
        assertThat(lastEvent.data()).containsEntry("status", "completed");

        service.chat("再说一次", first.conversationId());
        var other = service.chat("新话题", null);
        assertThat(other.conversationId()).isNotEqualTo(first.conversationId());

        var streamed = ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(streamed.capture());
        assertThat(streamed.getValue().getInstructions()).extracting(Message::getText)
                .containsExactly("用中文回答", "记住我的代号是青松", "收到", "我的代号是什么？");

        var complete = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(complete.capture());
        assertThat(complete.getAllValues().get(1).getInstructions()).extracting(Message::getText)
                .containsExactly("用中文回答", "记住我的代号是青松", "收到", "我的代号是什么？", "青松", "再说一次");
        assertThat(complete.getAllValues().get(2).getInstructions()).extracting(Message::getText)
                .containsExactly("用中文回答", "新话题");
    }

    @Test
    void keepsHistoryUntilCleanupThenStartsFreshWithTheSameId() {
        FirstAppService service = serviceWithWindow(20, 10);
        when(model.call(any(Prompt.class))).thenReturn(reply("收到"));
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(reply("青松")));
        var expired = memory.create(CreateSessionRequest.builder().timeToLive(Duration.ofNanos(1)).build());
        UUID id = UUID.fromString(expired.id());
        memory.appendMessage(expired.id(), new UserMessage("代号是青松"));
        memory.appendMessage(expired.id(), new AssistantMessage("记住了"));
        assertThat(expired.expiresAt()).isBefore(Instant.now());

        var continued = service.chat("请记住我的代号", id);
        assertThat(continued.expiresAt()).isEqualTo(expired.expiresAt());
        assertThat(lastChatPrompt().getInstructions()).extracting(Message::getText)
                .containsExactly("用中文回答", "代号是青松", "记住了", "请记住我的代号");

        var events = service.chatEventStream("我的代号是什么？", id)
                .collectList().block(Duration.ofSeconds(5));
        assertThat(events.get(0).data()).containsEntry("expiresAt", expired.expiresAt().toString());
        assertThat(events.get(events.size() - 1).data()).containsEntry("status", "completed");
        var streamed = ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(streamed.capture());
        assertThat(streamed.getValue().getInstructions()).extracting(Message::getText)
                .containsExactly("用中文回答", "代号是青松", "记住了", "请记住我的代号", "收到", "我的代号是什么？");

        assertThat(memory.deleteExpiredSessions(Instant.now())).isEqualTo(1);
        assertThat(memory.findById(expired.id())).isNull();

        var fresh = service.chat("新话题", id);
        assertThat(fresh.conversationId()).isEqualTo(id);
        assertThat(fresh.expiresAt()).isAfter(expired.expiresAt());
        assertThat(lastChatPrompt().getInstructions()).extracting(Message::getText)
                .containsExactly("用中文回答", "新话题");
    }

    @Test
    void keepsFourTurnsAtTheTriggerThreshold() throws Exception {
        SessionService observedMemory = spy(memory);
        UUID id = prepareConversation(observedMemory, 3);
        var attempted = observeNextCompaction(observedMemory, id);
        FirstAppService service = serviceWithWindow(observedMemory, 4, 2);

        assertThat(service.chat("问4", id).answer()).isEqualTo("答4");
        assertThat(attempted.get(5, TimeUnit.SECONDS).isUnchanged()).isTrue();
        verifyNoInteractions(summaryModel);
    }

    @ParameterizedTest(name = "returnsBeforeSummaryCompletes (SSE={0})")
    @ValueSource(booleans = {false, true})
    void returnsBeforeSummaryCompletes(boolean streamFifthTurn) throws Exception {
        var summaryStarted = new CountDownLatch(1);
        var releaseSummary = new CountDownLatch(1);
        when(summaryModel.call(any(Prompt.class))).thenAnswer(call -> {
            summaryStarted.countDown();
            if (!releaseSummary.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("测试未释放摘要模型");
            }
            return reply("前三轮固定摘要");
        });
        SessionService observedMemory = spy(memory);
        UUID id = prepareConversation(observedMemory, 4);
        var attempted = observeNextCompaction(observedMemory, id);
        FirstAppService service = serviceWithWindow(observedMemory, 4, 2);

        // 让真实聊天入口处理触发压缩的第 5 轮；即使改回同步压缩，测试也能超时退出。
        var fifthTurn = CompletableFuture.runAsync(() -> {
            if (streamFifthTurn) {
                assertFifthStreamCompletes(service, id);
            } else {
                assertThat(service.chat("问5", id).answer()).isEqualTo("答5");
            }
        });
        try {
            assertThat(summaryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            fifthTurn.get(5, TimeUnit.SECONDS);
            CompletableFuture.runAsync(() -> assertNextTurnReceivesAllFiveTurns(service, id))
                    .get(5, TimeUnit.SECONDS);
        } finally {
            releaseSummary.countDown();
        }

        // 等实际的 compact 返回，确保摘要已提交，再检查下一轮发给模型的消息。
        assertThat(attempted.get(5, TimeUnit.SECONDS).isUnchanged()).isFalse();
        var summaryCall = ArgumentCaptor.forClass(Prompt.class);
        verify(summaryModel).call(summaryCall.capture());
        assertThat(summaryCall.getValue().getUserMessage().getText())
                .contains("问1", "答1", "问2", "答2", "问3", "答3", "问4", "答4")
                .doesNotContain("问5", "答5", "问6");

        service.chat("问7", id);
        assertThat(lastChatPrompt().getInstructions()).extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        tuple(MessageType.SYSTEM, "用中文回答"),
                        tuple(MessageType.USER, ChatModelSummarizer.DEFAULT_SHADOW_PROMPT),
                        tuple(MessageType.ASSISTANT, "前三轮固定摘要"),
                        tuple(MessageType.USER, "问4"), tuple(MessageType.ASSISTANT, "答4"),
                        tuple(MessageType.USER, "问5"), tuple(MessageType.ASSISTANT, "答5"),
                        tuple(MessageType.USER, "问6"), tuple(MessageType.ASSISTANT, "答6"),
                        tuple(MessageType.USER, "问7"));
        assertThat(observedMemory.getMessages(id.toString())).extracting(Message::getText)
                .containsSequence("问1", "答1", "问2", "答2", "问3", "答3");
    }

    @Test
    void keepsHistoryWhenSummaryCallFails() throws Exception {
        when(summaryModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("模拟摘要调用失败"));
        assertHistoryAfterFailedCompaction();
    }

    @Test
    void keepsHistoryWhenSummaryIsBlank() throws Exception {
        when(summaryModel.call(any(Prompt.class))).thenReturn(reply("  \n"));
        assertHistoryAfterFailedCompaction();
    }

    private void assertHistoryAfterFailedCompaction() throws Exception {
        SessionService observedMemory = spy(memory);
        UUID id = prepareConversation(observedMemory, 4);
        var attempted = observeNextCompaction(observedMemory, id);
        FirstAppService service = serviceWithWindow(observedMemory, 4, 2);

        assertFifthStreamCompletes(service, id);
        assertThat(attempted.get(5, TimeUnit.SECONDS).isUnchanged()).isTrue();
        verify(summaryModel).call(any(Prompt.class));
        assertNextTurnReceivesAllFiveTurns(service, id);
    }

    private CompletableFuture<CompactionResult> observeNextCompaction(SessionService observedMemory, UUID id) {
        var attempted = new CompletableFuture<CompactionResult>();
        doAnswer(call -> {
            CompactionResult result = (CompactionResult) call.callRealMethod();
            attempted.complete(result);
            return result;
        }).when(observedMemory).compact(eq(id.toString()), any(), any());
        return attempted;
    }

    private UUID prepareConversation(SessionService sessionService, int previousTurns) {
        // 预置已完成的轮次，让本例的首次聊天独占后台压缩任务。
        String id = sessionService.create(CreateSessionRequest.builder().build()).id();
        for (int turn = 1; turn <= previousTurns; turn++) {
            sessionService.appendMessage(id, new UserMessage("问" + turn));
            sessionService.appendMessage(id, new AssistantMessage("答" + turn));
        }
        when(model.call(any(Prompt.class))).thenAnswer(call -> {
            Prompt prompt = call.getArgument(0);
            return reply(prompt.getUserMessage().getText().replace("问", "答"));
        });
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(reply("答"), reply("5")));
        return UUID.fromString(id);
    }

    private void assertNextTurnReceivesAllFiveTurns(FirstAppService service, UUID id) {
        assertThat(service.chat("问6", id).answer()).isEqualTo("答6");
        assertThat(lastChatPrompt().getInstructions()).extracting(Message::getText)
                .containsExactly("用中文回答", "问1", "答1", "问2", "答2", "问3", "答3", "问4", "答4", "问5", "答5", "问6");
    }

    private Prompt lastChatPrompt() {
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, atLeastOnce()).call(prompts.capture());
        return prompts.getValue();
    }

    private void assertFifthStreamCompletes(FirstAppService service, UUID id) {
        var events = service.chatEventStream("问5", id).collectList().block(Duration.ofSeconds(5));
        assertThat(events).isNotEmpty();
        String answer = events.stream()
                .filter(event -> "answer.delta".equals(event.event()))
                .map(event -> (String) event.data().get("text"))
                .collect(Collectors.joining());
        assertThat(answer).isEqualTo("答5");
        var lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.event()).isEqualTo("turn.finished");
        assertThat(lastEvent.data()).containsEntry("status", "completed");
    }

    private FirstAppService serviceWithWindow(int triggerTurns, int keepTurns) {
        return serviceWithWindow(memory, triggerTurns, keepTurns);
    }

    private FirstAppService serviceWithWindow(SessionService memory, int triggerTurns, int keepTurns) {
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        var system = new ByteArrayResource("用中文回答".getBytes(StandardCharsets.UTF_8));
        return new FirstAppServiceImpl(ChatClient.builder(model), memory, system, triggerTurns, keepTurns,
                configuration.chatModelSummarizer(summaryModel));
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
