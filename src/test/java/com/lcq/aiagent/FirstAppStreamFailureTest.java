package com.lcq.aiagent;

import com.lcq.aiagent.ai.config.ModelConfiguration;
import com.lcq.aiagent.chat.service.impl.FirstAppServiceImpl;
import com.lcq.aiagent.chat.sse.FirstAppStreamError;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import javax.net.ssl.SSLHandshakeException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirstAppStreamFailureTest {

    static Stream<Arguments> failures() {
        var tls = new OpenAIIoException("internal detail", new SSLHandshakeException("internal detail"));
        return Stream.of(
                Arguments.of(Flux.error(new CompletionException(tls)), "upstream_tls"),
                Arguments.of(Flux.error(new SocketTimeoutException("internal detail")), "upstream_timeout"),
                Arguments.of(Flux.error(new UnknownHostException("internal detail")), "upstream_network"),
                Arguments.of(Flux.error(serviceError(403)), "upstream_access"),
                Arguments.of(Flux.error(serviceError(429)), "upstream_rate_limit"),
                Arguments.of(Flux.error(serviceError(503)), "upstream_unavailable"),
                Arguments.of(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(""))))), "empty_response"),
                Arguments.of(Flux.error(new IllegalStateException("internal detail")), "generation_failed"));
    }

    private static OpenAIServiceException serviceError(int status) {
        var error = mock(OpenAIServiceException.class);
        when(error.statusCode()).thenReturn(status);
        return error;
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("failures")
    void finishesWithOneClientSafeReason(Flux<ChatResponse> modelResponse, String code) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.stream(any(Prompt.class))).thenReturn(modelResponse);
        var memory = DefaultSessionService.builder()
                .sessionRepository(InMemorySessionRepository.builder().build())
                .build();
        ChatModel summaryModel = mock(ChatModel.class);
        var summarizer = new ModelConfiguration().chatModelSummarizer(summaryModel);
        var service = new FirstAppServiceImpl(ChatClient.builder(model), memory,
                new ClassPathResource("prompts/first-app.txt"), 20, 10, summarizer);

        var events = service.chatEventStream("你好", null).collectList().block(Duration.ofSeconds(5));
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("turn.started", "generation.started", "turn.finished");
        var data = events.get(2).data();
        assertThat(data).containsEntry("status", "failed").containsOnlyKeys("status", "error");
        var reason = (FirstAppStreamError) data.get("error");
        assertThat(reason.code()).isEqualTo(code);
        assertThat(reason.message()).isNotBlank().doesNotContain("internal detail");
        verify(summaryModel, never()).call(any(Prompt.class));
    }
}
