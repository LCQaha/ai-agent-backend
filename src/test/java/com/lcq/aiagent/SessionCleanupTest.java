package com.lcq.aiagent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.SessionService;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "OPENAI_API_KEY=local-test-only",
        "AI_SESSION_CLEANUP_INTERVAL=50ms"
})
class SessionCleanupTest {

    @Resource
    private SessionService sessionService;

    @Test
    void scheduledCleanupRemovesExpiredSessionsAndPreservesActiveHistory() {
        var active = sessionService.create(CreateSessionRequest.builder().build());
        sessionService.appendMessage(active.id(), new UserMessage("保留这条消息"));
        var expiring = sessionService.create(CreateSessionRequest.builder()
                .timeToLive(Duration.ofMillis(150))
                .build());

        await().atMost(Duration.ofSeconds(5))
                .until(() -> sessionService.findById(expiring.id()) == null);

        assertThat(sessionService.findById(active.id())).isNotNull();
        assertThat(sessionService.getMessages(active.id())).extracting(Message::getText)
                .containsExactly("保留这条消息");
    }
}
